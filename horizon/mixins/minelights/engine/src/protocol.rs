use std::{
    collections::{HashMap, HashSet, VecDeque},
    fs,
    io::{Read, Write},
    net::{TcpStream, ToSocketAddrs},
    path::{Path, PathBuf},
    sync::Arc,
    time::{Duration, Instant},
};

use base64::{Engine as _, engine::general_purpose::URL_SAFE_NO_PAD};
use hmac::{Hmac, Mac};
use rustls::{
    ClientConfig, ClientConnection, StreamOwned,
    client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier},
    crypto::{self, WebPkiSupportedAlgorithms},
    pki_types::{CertificateDer, ServerName, UnixTime},
};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use sha2::{Digest, Sha256};

use super::DeviceLayout;

const MAGIC: &[u8; 4] = b"MLV2";
const HEADER_SIZE: usize = 16;
const MAX_CONTROL_MESSAGE: usize = 1024 * 1024;
const MAX_MESSAGE: usize = 8 * 1024 * 1024;
const PROTOCOL_MAJOR: u16 = 2;
const MIN_SUPPORTED_MINOR: u16 = 0;
const MAX_SUPPORTED_MINOR: u16 = 0;
const MAX_OUTPUT_ELEMENTS_PER_SERVER: u32 = 100_000;

const CLIENT_HELLO: u16 = 0x0001;
const SERVER_HELLO: u16 = 0x0002;
const CLIENT_AUTH: u16 = 0x0003;
const AUTH_RESULT: u16 = 0x0004;
const DEVICE_LIST: u16 = 0x0010;
const DEVICE_CHANGED: u16 = 0x0011;
const LIGHTING_FRAME: u16 = 0x0020;
const PING: u16 = 0x0030;
const PONG: u16 = 0x0031;
const REQUEST: u16 = 0x0040;
const RESPONSE: u16 = 0x0041;
const ERROR: u16 = 0x00fe;
const GOODBYE: u16 = 0x00ff;

type HmacSha256 = Hmac<Sha256>;
type TlsStream = StreamOwned<ClientConnection, TcpStream>;

#[derive(Clone, Debug, Deserialize, Serialize, PartialEq, Eq)]
pub struct Endpoint {
    pub server_id: String,
    pub server_name: String,
    pub host: String,
    pub port: u16,
    pub tls_certificate_sha256: String,
}

#[derive(Clone, Deserialize, Serialize)]
pub struct RemoteServer {
    pub endpoint: Endpoint,
    pub client_id: String,
    pub client_secret: String,
}

#[derive(Clone, Serialize, Deserialize)]
struct ClientIdentity {
    client_id: String,
    client_secret: String,
}

#[derive(Default, Serialize, Deserialize)]
struct ClientStore {
    local: Option<ClientIdentity>,
    #[serde(default)]
    remotes: Vec<RemoteServer>,
}

#[derive(Deserialize)]
struct EndpointDocument {
    schema_version: u32,
    server_id: String,
    server_name: String,
    host: String,
    port: u16,
    tls_certificate_sha256: String,
    protocol_major: u16,
    protocol_minor: u16,
}

#[derive(Deserialize)]
struct PairingFile {
    schema_version: u32,
    server_id: String,
    host: String,
    port: u16,
    tls_certificate_sha256: String,
    pairing_id: String,
    pairing_code: String,
    expires_at_utc: String,
}

#[derive(Deserialize)]
struct ServerHello {
    server_id: String,
    #[serde(rename = "server_name")]
    _server_name: String,
    selected_minor: u16,
    nonce: String,
}

#[derive(Clone, Deserialize)]
struct DeviceList {
    revision: u64,
    devices: Vec<DeviceInfo>,
}

#[derive(Clone, Deserialize)]
struct DeviceInfo {
    device_id: String,
    name: String,
    #[serde(default)]
    vendor: String,
    integration: String,
    #[serde(default)]
    elements: Vec<ElementInfo>,
    #[serde(default)]
    capabilities: Vec<String>,
}

#[derive(Clone, Deserialize)]
struct ElementInfo {
    element_id: u32,
    #[serde(default)]
    key: Option<String>,
    #[serde(default)]
    zone: Option<String>,
}

#[derive(Deserialize)]
struct Envelope {
    #[serde(default)]
    request_id: Option<String>,
    #[serde(default)]
    ok: Option<bool>,
    #[serde(default)]
    error_code: Option<String>,
    #[serde(default)]
    result: Option<Value>,
    #[serde(default)]
    scopes: Vec<String>,
}

#[derive(Clone, Debug)]
pub struct V2Device {
    pub device_id: String,
    pub layout: DeviceLayout,
    wire_elements: Vec<u32>,
    wire_keys: HashMap<String, Vec<u32>>,
}

struct Message {
    minor: u16,
    kind: u16,
    body: Value,
}

pub struct V2Connection {
    server_id: String,
    client_id: String,
    selected_minor: u16,
    endpoint: Endpoint,
    output_slot: u32,
    stream: TlsStream,
    read_buffer: Vec<u8>,
    pending_events: VecDeque<Message>,
    sequence: u32,
    revision: u64,
    last_ping: Instant,
    last_lease_retry: Instant,
    leased_devices: HashSet<String>,
    output_targets: HashMap<u32, (String, u32)>,
    pub devices: Vec<V2Device>,
}

impl V2Connection {
    pub fn server_id(&self) -> &str {
        &self.server_id
    }

    pub fn matches_endpoint(&self, endpoint: &Endpoint) -> bool {
        &self.endpoint == endpoint
    }

    pub fn matches_remote(&self, server: &RemoteServer) -> bool {
        self.matches_endpoint(&server.endpoint) && self.client_id == server.client_id
    }

    pub fn acquire_devices(&mut self) {
        let device_ids = self
            .devices
            .iter()
            .filter(|device| !self.leased_devices.contains(&device.device_id))
            .map(|device| device.device_id.clone())
            .collect::<Vec<_>>();
        if device_ids.is_empty() {
            return;
        }
        let Ok(request_id) = random_request_id() else {
            return;
        };
        let _ = self
            .stream
            .sock
            .set_read_timeout(Some(Duration::from_secs(3)));
        if let Ok(result) = self.request(
            &request_id,
            "lighting.acquire",
            json!({"device_ids": device_ids.clone()}),
        ) {
            if let Some(granted) = result.get("granted_device_ids").and_then(Value::as_array) {
                self.leased_devices
                    .extend(granted.iter().filter_map(Value::as_str).map(str::to_owned));
            }
        }
        // A server may reject an all-device acquire because one device is
        // leased by another client. Retry each ungranted ID so other devices
        // remain usable in this session.
        for device_id in device_ids {
            if self.leased_devices.contains(&device_id) {
                continue;
            }
            let Ok(request_id) = random_request_id() else {
                break;
            };
            if let Ok(result) = self.request(
                &request_id,
                "lighting.acquire",
                json!({"device_ids": [device_id.clone()]}),
            ) {
                if result
                    .get("granted_device_ids")
                    .and_then(Value::as_array)
                    .is_some_and(|granted| {
                        granted
                            .iter()
                            .any(|id| id.as_str() == Some(device_id.as_str()))
                    })
                {
                    self.leased_devices.insert(device_id);
                }
            }
        }
        let _ = self
            .stream
            .sock
            .set_read_timeout(Some(Duration::from_millis(1)));
    }

    fn release_devices(&mut self, device_ids: &[String]) {
        if device_ids.is_empty() {
            return;
        }
        let Ok(request_id) = random_request_id() else {
            return;
        };
        let _ = self
            .stream
            .sock
            .set_read_timeout(Some(Duration::from_secs(3)));
        if let Ok(result) = self.request(
            &request_id,
            "lighting.release",
            json!({"device_ids": device_ids}),
        ) {
            if let Some(released) = result.get("released_device_ids").and_then(Value::as_array) {
                for device_id in released.iter().filter_map(Value::as_str) {
                    self.leased_devices.remove(device_id);
                }
            }
        }
        let _ = self
            .stream
            .sock
            .set_read_timeout(Some(Duration::from_millis(1)));
    }

    pub fn assign_output_ids(&mut self, session_slot: u32) -> Vec<DeviceLayout> {
        self.output_slot = session_slot;
        self.output_targets.clear();
        let mut next = 0u32;
        let mut layouts = Vec::with_capacity(self.devices.len());
        for device in &mut self.devices {
            let mut layout = device.layout.clone();
            layout.leds.clear();
            layout.keys.clear();
            let mut keys_by_element: HashMap<u32, Vec<String>> = HashMap::new();
            for (key, elements) in &device.wire_keys {
                for element in elements {
                    keys_by_element
                        .entry(*element)
                        .or_default()
                        .push(key.clone());
                }
            }
            let mut seen = HashSet::new();
            for element in device.wire_elements.iter().copied() {
                if !seen.insert(element) {
                    continue;
                }
                if next >= MAX_OUTPUT_ELEMENTS_PER_SERVER {
                    break;
                }
                let output_id = 0x4000_0000 | ((session_slot & 0x3ff) << 19) | next;
                next += 1;
                self.output_targets
                    .insert(output_id, (device.device_id.clone(), element));
                layout.leds.push(output_id);
                if let Some(keys) = keys_by_element.get(&element) {
                    for key in keys {
                        layout.keys.entry(key.clone()).or_default().push(output_id);
                    }
                }
            }
            device.layout = layout.clone();
            layouts.push(layout);
        }
        layouts
    }

    pub fn send_frame(&mut self, frame: &HashMap<u32, [u8; 3]>) -> bool {
        let mut grouped: HashMap<String, Vec<Value>> = HashMap::new();
        for (output_id, color) in frame {
            let Some((device_id, element_id)) = self.output_targets.get(output_id) else {
                continue;
            };
            if !self.leased_devices.contains(device_id) {
                continue;
            }
            grouped.entry(device_id.clone()).or_default().push(json!({
                "element_id": element_id,
                "r": color[0],
                "g": color[1],
                "b": color[2]
            }));
        }
        if grouped.is_empty() {
            return true;
        }
        let targets = grouped
            .into_iter()
            .map(|(device_id, colors)| json!({"device_id": device_id, "colors": colors}))
            .collect::<Vec<_>>();
        self.sequence = self.sequence.wrapping_add(1);
        self.write_message(
            LIGHTING_FRAME,
            json!({"sequence": self.sequence, "ttl_ms": 1000, "targets": targets}),
        )
        .is_ok()
    }

    pub fn matches_launch_id(&mut self, expected: &str) -> bool {
        let Ok(id) = random_request_id() else { return false; };
        let _ = self.stream.sock.set_read_timeout(Some(Duration::from_secs(3)));
        let matches = self.request(&id, "server.info", json!({})).ok()
            .and_then(|value| value.get("launch_id").and_then(Value::as_str).map(|id| id == expected))
            .unwrap_or(false);
        let _ = self.stream.sock.set_read_timeout(Some(Duration::from_millis(1)));
        matches
    }

    pub fn request_server_shutdown(&mut self) -> &'static str {
        let Ok(id) = random_request_id() else { return "unavailable"; };
        let _ = self.stream.sock.set_read_timeout(Some(Duration::from_secs(3)));
        let result = self.request(&id, "server.shutdown", json!({}));
        let outcome = match result {
            Ok(value) if value.get("accepted").and_then(Value::as_bool) == Some(true) => "accepted",
            Ok(value) if value.get("pending_for_ms").and_then(Value::as_u64).unwrap_or(0) > 0 => "pending",
            Ok(_) => "other_clients",
            Err(_) => "unavailable",
        };
        let _ = self.stream.sock.set_read_timeout(Some(Duration::from_millis(1)));
        outcome
    }

    pub fn poll(&mut self) -> Result<Option<Vec<DeviceLayout>>, String> {
        if self.last_lease_retry.elapsed() >= Duration::from_secs(5) {
            self.leased_devices.clear();
            self.acquire_devices();
            self.last_lease_retry = Instant::now();
        }
        if self.last_ping.elapsed() >= Duration::from_secs(2) {
            self.write_message(PING, json!({"nonce": random_request_id()?}))?;
            self.last_ping = Instant::now();
        }
        let message = if let Some(message) = self.pending_events.pop_front() {
            message
        } else {
            let Some(message) = self.read_message_nonblocking()? else {
                return Ok(None);
            };
            message
        };
        match message.kind {
            DEVICE_LIST | DEVICE_CHANGED => {
                let snapshot: DeviceList = serde_json::from_value(message.body)
                    .map_err(|error| format!("invalid DEVICE_CHANGED: {error}"))?;
                if snapshot.revision <= self.revision {
                    return Ok(None);
                }
                self.revision = snapshot.revision;
                let devices = build_layouts(&self.server_id, snapshot);
                let current_ids = devices
                    .iter()
                    .map(|device| device.device_id.clone())
                    .collect::<HashSet<_>>();
                let removed = self
                    .leased_devices
                    .iter()
                    .filter(|device_id| !current_ids.contains(*device_id))
                    .cloned()
                    .collect::<Vec<_>>();
                self.release_devices(&removed);
                self.leased_devices
                    .retain(|device_id| current_ids.contains(device_id));
                self.devices = devices.clone();
                self.assign_output_ids(self.output_slot);
                self.acquire_devices();
                Ok(Some(
                    self.devices
                        .iter()
                        .map(|device| device.layout.clone())
                        .collect(),
                ))
            }
            PING => {
                self.write_message(PONG, message.body)?;
                Ok(None)
            }
            ERROR if message.body.get("error_code").and_then(Value::as_str) == Some("LEASE_REQUIRED") => {
                self.leased_devices.clear();
                Ok(None)
            }
            ERROR => Err(message
                .body
                .get("message")
                .and_then(Value::as_str)
                .unwrap_or("server returned an error")
                .to_owned()),
            GOODBYE => Err("server closed the V2 session".into()),
            _ => Ok(None),
        }
    }

    fn request(
        &mut self,
        request_id: &str,
        operation: &str,
        params: Value,
    ) -> Result<Value, String> {
        let deadline = Instant::now() + Duration::from_secs(3);
        self.write_message(
            REQUEST,
            json!({"request_id": request_id, "operation": operation, "params": params}),
        )?;
        loop {
            if Instant::now() >= deadline {
                return Err("V2 request timed out".into());
            }
            let message = self.read_message_blocking()?;
            if message.kind == PING {
                self.write_message(PONG, message.body)?;
                continue;
            }
            if matches!(message.kind, DEVICE_LIST | DEVICE_CHANGED) {
                if self.pending_events.len() >= 16 {
                    self.pending_events.pop_front();
                }
                self.pending_events.push_back(message);
                continue;
            }
            if message.kind == ERROR {
                return Err(message
                    .body
                    .get("message")
                    .and_then(Value::as_str)
                    .unwrap_or("server request failed")
                    .to_owned());
            }
            if message.kind != RESPONSE {
                continue;
            }
            let response: Envelope = serde_json::from_value(message.body)
                .map_err(|error| format!("invalid V2 response: {error}"))?;
            if response.request_id.as_deref() != Some(request_id) {
                continue;
            }
            if response.ok != Some(true) {
                return Err(response
                    .error_code
                    .unwrap_or_else(|| "request rejected".into()));
            }
            return Ok(response.result.unwrap_or(Value::Null));
        }
    }

    fn write_message(&mut self, kind: u16, body: Value) -> Result<(), String> {
        write_message(&mut self.stream, kind, &body, self.selected_minor)
    }

    fn read_message_blocking(&mut self) -> Result<Message, String> {
        let mut header = [0u8; HEADER_SIZE];
        self.stream
            .read_exact(&mut header)
            .map_err(|error| format!("read V2 header: {error}"))?;
        let (minor, kind, length) = parse_header(&header, Some(self.selected_minor))?;
        let mut payload = vec![0; length];
        self.stream
            .read_exact(&mut payload)
            .map_err(|error| format!("read V2 payload: {error}"))?;
        parse_message(minor, kind, &payload)
    }

    fn read_message_nonblocking(&mut self) -> Result<Option<Message>, String> {
        if self.read_buffer.len() >= HEADER_SIZE {
            let (minor, kind, length) =
                parse_header(&self.read_buffer[..HEADER_SIZE], Some(self.selected_minor))?;
            let total = HEADER_SIZE + length;
            if self.read_buffer.len() >= total {
                let payload = self.read_buffer[HEADER_SIZE..total].to_vec();
                self.read_buffer.drain(..total);
                return parse_message(minor, kind, &payload).map(Some);
            }
        }
        let mut bytes = [0u8; 8192];
        match self.stream.read(&mut bytes) {
            Ok(0) => Err("V2 server disconnected".into()),
            Ok(length) => {
                self.read_buffer.extend_from_slice(&bytes[..length]);
                if self.read_buffer.len() >= HEADER_SIZE {
                    let (minor, kind, payload_length) =
                        parse_header(&self.read_buffer[..HEADER_SIZE], Some(self.selected_minor))?;
                    let total = HEADER_SIZE + payload_length;
                    if self.read_buffer.len() >= total {
                        let payload = self.read_buffer[HEADER_SIZE..total].to_vec();
                        self.read_buffer.drain(..total);
                        return parse_message(minor, kind, &payload).map(Some);
                    }
                }
                Ok(None)
            }
            Err(error)
                if matches!(
                    error.kind(),
                    std::io::ErrorKind::WouldBlock | std::io::ErrorKind::TimedOut
                ) =>
            {
                Ok(None)
            }
            Err(error) => Err(format!("read V2 session: {error}")),
        }
    }
}

impl Drop for V2Connection {
    fn drop(&mut self) {
        let _ = self
            .stream
            .sock
            .set_write_timeout(Some(Duration::from_millis(250)));
        if !self.leased_devices.is_empty() {
            let ids = self.leased_devices.iter().cloned().collect::<Vec<_>>();
            if let Ok(request_id) = random_request_id() {
                let _ = self.write_message(
                    REQUEST,
                    json!({
                        "request_id": request_id,
                        "operation": "lighting.release",
                        "params": {"device_ids": ids}
                    }),
                );
            }
        }
        let _ = self.write_message(GOODBYE, json!({"reason": "client_disconnect"}));
    }
}

pub fn local_endpoint_path() -> Option<PathBuf> {
    Some(local_data_dir()?.join("endpoint.json"))
}

pub fn local_endpoint() -> Result<Option<Endpoint>, String> {
    let Some(path) = local_endpoint_path() else {
        return Ok(None);
    };
    if !path.is_file() {
        return Ok(None);
    }
    let bytes = read_bounded(&path, 16 * 1024)?;
    let document: EndpointDocument = serde_json::from_slice(&bytes)
        .map_err(|error| format!("invalid V2 endpoint document: {error}"))?;
    if document.schema_version != 1
        || document.protocol_major != PROTOCOL_MAJOR
        || document.protocol_minor > MAX_SUPPORTED_MINOR
        || document.host != "127.0.0.1"
        || document.port == 0
    {
        return Err("unsupported or invalid local V2 endpoint".into());
    }
    validate_uuid(&document.server_id)?;
    validate_fingerprint(&document.tls_certificate_sha256)?;
    let address = std::net::SocketAddr::from(([127, 0, 0, 1], document.port));
    if TcpStream::connect_timeout(&address, Duration::from_millis(300)).is_err() {
        // A stale discovery document after a server crash is not an active V2 endpoint.
        return Ok(None);
    }
    Ok(Some(Endpoint {
        server_id: document.server_id,
        server_name: document.server_name,
        host: document.host,
        port: document.port,
        tls_certificate_sha256: document.tls_certificate_sha256,
    }))
}

pub fn local_enrollment_key() -> Result<Option<[u8; 32]>, String> {
    let Some(path) = local_data_dir().map(|dir| dir.join("local-enrollment.key")) else {
        return Ok(None);
    };
    if !path.is_file() {
        return Ok(None);
    }
    let bytes = read_bounded(&path, 32)?;
    let key: [u8; 32] = bytes
        .try_into()
        .map_err(|_| "local-enrollment.key must contain exactly 32 bytes".to_owned())?;
    Ok(Some(key))
}

pub fn connect_local(config_dir: &Path) -> Result<Option<(V2Connection, Vec<V2Device>)>, String> {
    connect_local_internal(config_dir, true)
}

pub fn connect_local_control(config_dir: &Path) -> Result<Option<(V2Connection, Vec<V2Device>)>, String> {
    connect_local_internal(config_dir, false)
}

fn connect_local_internal(config_dir: &Path, acquire: bool) -> Result<Option<(V2Connection, Vec<V2Device>)>, String> {
    let Some(endpoint) = local_endpoint()? else {
        return Ok(None);
    };
    let mut store = load_store(config_dir)?;
    let identity = match store.local.clone() {
        Some(identity) => identity,
        None => {
            let identity = new_identity()?;
            store.local = Some(identity.clone());
            save_store(config_dir, &store)?;
            identity
        }
    };
    match connect_authenticated(
        &endpoint,
        &identity,
        "client_secret",
        &identity.client_id,
        &decode_secret(&identity.client_secret)?,
        acquire,
    ) {
        Ok((connection, devices)) => return Ok(Some((connection, devices))),
        Err(error) if error == "UNKNOWN_CLIENT" => {}
        Err(error) => return Err(error),
    }
    let Some(enrollment_key) = local_enrollment_key()? else {
        return Err("local V2 server has no enrollment key".into());
    };
    enroll_client(
        &endpoint,
        &identity,
        "local_enrollment",
        "local",
        &enrollment_key,
    )?;
    let (connection, devices) = connect_authenticated(
        &endpoint,
        &identity,
        "client_secret",
        &identity.client_id,
        &decode_secret(&identity.client_secret)?,
        acquire,
    )?;
    Ok(Some((connection, devices)))
}

pub fn load_remote_servers(config_dir: &Path) -> Result<Vec<RemoteServer>, String> {
    Ok(load_store(config_dir)?.remotes)
}

pub fn remove_remote_server(config_dir: &Path, server_id: &str) -> Result<bool, String> {
    validate_uuid(server_id)?;
    let mut store = load_store(config_dir)?;
    let previous_len = store.remotes.len();
    store
        .remotes
        .retain(|server| server.endpoint.server_id != server_id);
    if store.remotes.len() == previous_len {
        return Ok(false);
    }
    save_store(config_dir, &store)?;
    Ok(true)
}

pub fn import_remote_pairing(config_dir: &Path, path: &Path) -> Result<String, String> {
    let bytes = read_bounded(path, 16 * 1024)?;
    let pairing: PairingFile =
        serde_json::from_slice(&bytes).map_err(|error| format!("invalid .mlpair file: {error}"))?;
    if pairing.schema_version != 1 || pairing.port == 0 {
        return Err("unsupported or invalid .mlpair file".into());
    }
    validate_fingerprint(&pairing.tls_certificate_sha256)?;
    validate_uuid(&pairing.server_id)?;
    validate_uuid(&pairing.pairing_id)?;
    if !pairing.expires_at_utc.ends_with('Z')
        || parse_utc_expiry(&pairing.expires_at_utc)? <= std::time::SystemTime::now()
    {
        return Err("the server pairing file has expired".into());
    }
    let pairing_code = decode_secret(&pairing.pairing_code)?;
    let endpoint = Endpoint {
        server_id: pairing.server_id,
        server_name: String::new(),
        host: pairing.host,
        port: pairing.port,
        tls_certificate_sha256: pairing.tls_certificate_sha256,
    };
    let identity = new_identity()?;
    let mut store = load_store(config_dir)?;
    let previous = store
        .remotes
        .iter()
        .filter(|server| server.endpoint.server_id == endpoint.server_id)
        .cloned()
        .collect::<Vec<_>>();
    store
        .remotes
        .retain(|server| server.endpoint.server_id != endpoint.server_id);
    let remote = RemoteServer {
        endpoint: endpoint.clone(),
        client_id: identity.client_id.clone(),
        client_secret: identity.client_secret.clone(),
    };
    store.remotes.push(remote.clone());
    save_store(config_dir, &store)?;
    if let Err(enroll_error) = enroll_client(
        &endpoint,
        &identity,
        "pairing_code",
        &pairing.pairing_id,
        &pairing_code,
    ) {
        // Registration may have succeeded even when its response was lost.
        if let Ok((connection, _)) = connect_remote(&remote) {
            drop(connection);
            return Ok(format!("Paired MineLights Server {}.", endpoint.server_id));
        }
        let mut restored = load_store(config_dir)?;
        restored
            .remotes
            .retain(|server| server.endpoint.server_id != endpoint.server_id);
        restored.remotes.extend(previous);
        save_store(config_dir, &restored)?;
        return Err(enroll_error);
    }
    Ok(format!("Paired MineLights Server {}.", endpoint.server_id))
}

pub fn connect_remote(server: &RemoteServer) -> Result<(V2Connection, Vec<V2Device>), String> {
    connect_remote_internal(server, true)
}

pub fn connect_remote_control(server: &RemoteServer) -> Result<(V2Connection, Vec<V2Device>), String> {
    connect_remote_internal(server, false)
}

fn connect_remote_internal(server: &RemoteServer, acquire: bool) -> Result<(V2Connection, Vec<V2Device>), String> {
    let identity = ClientIdentity {
        client_id: server.client_id.clone(),
        client_secret: server.client_secret.clone(),
    };
    connect_authenticated(
        &server.endpoint,
        &identity,
        "client_secret",
        &identity.client_id,
        &decode_secret(&identity.client_secret)?,
        acquire,
    )
}

fn connect_authenticated(
    endpoint: &Endpoint,
    identity: &ClientIdentity,
    credential_type: &str,
    credential_id: &str,
    key: &[u8; 32],
    acquire: bool,
) -> Result<(V2Connection, Vec<V2Device>), String> {
    let (mut stream, hello) =
        authenticate(endpoint, identity, credential_type, credential_id, key)?;
    let device_message = read_message_blocking(&mut stream, Some(hello.selected_minor))?;
    if device_message.kind != DEVICE_LIST {
        return Err("server did not send DEVICE_LIST after authentication".into());
    }
    let snapshot: DeviceList = serde_json::from_value(device_message.body)
        .map_err(|error| format!("invalid DEVICE_LIST: {error}"))?;
    let revision = snapshot.revision;
    let devices = build_layouts(&hello.server_id, snapshot);
    let mut connection = V2Connection {
        server_id: hello.server_id,
        client_id: identity.client_id.clone(),
        selected_minor: hello.selected_minor,
        endpoint: endpoint.clone(),
        output_slot: 0,
        stream,
        read_buffer: Vec::with_capacity(16 * 1024),
        pending_events: VecDeque::new(),
        sequence: 0,
        revision,
        last_ping: Instant::now(),
        last_lease_retry: Instant::now(),
        leased_devices: HashSet::new(),
        output_targets: HashMap::new(),
        devices: devices.clone(),
    };
    if acquire { connection.acquire_devices(); }
    connection
        .stream
        .sock
        .set_read_timeout(Some(Duration::from_millis(1)))
        .map_err(|error| format!("configure V2 session timeout: {error}"))?;
    Ok((connection, devices))
}

fn authenticate(
    endpoint: &Endpoint,
    identity: &ClientIdentity,
    credential_type: &str,
    credential_id: &str,
    key: &[u8; 32],
) -> Result<(TlsStream, ServerHello), String> {
    validate_endpoint(endpoint)?;
    let mut stream = open_tls(endpoint)?;
    write_message(
        &mut stream,
        CLIENT_HELLO,
        &json!({
            "client_id": identity.client_id,
            "app_id": "megabytesme.minelights.horizon",
            "app_version": "2.4.0",
            "min_minor": MIN_SUPPORTED_MINOR,
            "max_minor": MAX_SUPPORTED_MINOR,
            "capabilities": ["devices.read", "lighting.write"]
        }),
        MAX_SUPPORTED_MINOR,
    )?;
    let server_message = read_message_blocking(&mut stream, None)?;
    if server_message.kind != SERVER_HELLO {
        return Err("server did not return SERVER_HELLO".into());
    }
    let hello: ServerHello = serde_json::from_value(server_message.body)
        .map_err(|error| format!("invalid SERVER_HELLO: {error}"))?;
    if hello.server_id != endpoint.server_id
        || hello.selected_minor < MIN_SUPPORTED_MINOR
        || hello.selected_minor > MAX_SUPPORTED_MINOR
        || server_message.minor != hello.selected_minor
    {
        return Err("server identity or protocol version did not match the paired endpoint".into());
    }
    let server_nonce = decode_base64_32(&hello.nonce)?;
    let mut client_nonce = [0u8; 32];
    getrandom::fill(&mut client_nonce)
        .map_err(|error| format!("secure random unavailable: {error}"))?;
    let proof = auth_proof(
        key,
        credential_type,
        credential_id,
        &hello.server_id,
        &identity.client_id,
        &server_nonce,
        &client_nonce,
        hello.selected_minor,
    );
    write_message(
        &mut stream,
        CLIENT_AUTH,
        &json!({
            "credential_type": credential_type,
            "credential_id": credential_id,
            "client_nonce": URL_SAFE_NO_PAD.encode(client_nonce),
            "proof": URL_SAFE_NO_PAD.encode(proof)
        }),
        hello.selected_minor,
    )?;
    let auth_message = read_message_blocking(&mut stream, Some(hello.selected_minor))?;
    if auth_message.kind != AUTH_RESULT {
        return Err("server did not return AUTH_RESULT".into());
    }
    let auth: Envelope = serde_json::from_value(auth_message.body)
        .map_err(|error| format!("invalid AUTH_RESULT: {error}"))?;
    if auth.ok != Some(true) {
        return Err(auth
            .error_code
            .unwrap_or_else(|| "authentication rejected".into()));
    }
    let required_scopes: &[&str] =
        if credential_type == "local_enrollment" || credential_type == "pairing_code" {
            &["client.enroll"]
        } else {
            &["devices.read", "lighting.write"]
        };
    if required_scopes
        .iter()
        .any(|required| !auth.scopes.iter().any(|scope| scope == required))
    {
        return Err("server did not grant the requested MineLights scopes".into());
    }
    Ok((stream, hello))
}

fn enroll_client(
    endpoint: &Endpoint,
    identity: &ClientIdentity,
    credential_type: &str,
    credential_id: &str,
    key: &[u8; 32],
) -> Result<(), String> {
    let (mut stream, hello) =
        authenticate(endpoint, identity, credential_type, credential_id, key)?;
    register_client(&mut stream, identity, hello.selected_minor)?;
    let _ = stream.sock.shutdown(std::net::Shutdown::Both);
    Ok(())
}

fn register_client(
    stream: &mut TlsStream,
    identity: &ClientIdentity,
    selected_minor: u16,
) -> Result<(), String> {
    let request_id = random_request_id()?;
    write_message(
        stream,
        REQUEST,
        &json!({
            "request_id": request_id,
            "operation": "client.register",
            "params": {
                "client_id": identity.client_id,
                "client_secret": identity.client_secret,
                "app_id": "megabytesme.minelights.horizon",
                "app_version": "2.4.0",
                "display_name": "MineLights for Horizon",
                "requested_scopes": ["devices.read", "lighting.write"]
            }
        }),
        selected_minor,
    )?;
    let response = read_message_blocking(stream, Some(selected_minor))?;
    if response.kind != RESPONSE {
        return Err("server did not return client.register response".into());
    }
    let envelope: Envelope = serde_json::from_value(response.body)
        .map_err(|error| format!("invalid client.register response: {error}"))?;
    if envelope.request_id.as_deref() != Some(request_id.as_str()) || envelope.ok != Some(true) {
        return Err(envelope
            .error_code
            .unwrap_or_else(|| "client registration rejected".into()));
    }
    let granted_scopes = envelope
        .result
        .as_ref()
        .and_then(|result| result.get("granted_scopes"))
        .and_then(Value::as_array)
        .ok_or_else(|| "client registration response omitted granted scopes".to_owned())?;
    if !granted_scopes
        .iter()
        .any(|scope| scope.as_str() == Some("devices.read"))
        || !granted_scopes
            .iter()
            .any(|scope| scope.as_str() == Some("lighting.write"))
    {
        return Err(
            "server did not grant the required MineLights device and lighting scopes".into(),
        );
    }
    Ok(())
}

fn open_tls(endpoint: &Endpoint) -> Result<TlsStream, String> {
    validate_fingerprint(&endpoint.tls_certificate_sha256)?;
    let addresses = (endpoint.host.as_str(), endpoint.port)
        .to_socket_addrs()
        .map_err(|error| format!("resolve server endpoint: {error}"))?
        .collect::<Vec<_>>();
    let mut socket = None;
    let mut last_error = None;
    for address in addresses {
        match TcpStream::connect_timeout(&address, Duration::from_secs(3)) {
            Ok(connected) => {
                socket = Some(connected);
                break;
            }
            Err(error) => last_error = Some(error),
        }
    }
    let mut socket = socket.ok_or_else(|| {
        last_error.map_or_else(
            || "server endpoint resolved to no addresses".to_owned(),
            |error| format!("connect to MineLights Server: {error}"),
        )
    })?;
    socket
        .set_read_timeout(Some(Duration::from_secs(3)))
        .map_err(|error| format!("configure V2 read timeout: {error}"))?;
    socket
        .set_write_timeout(Some(Duration::from_secs(3)))
        .map_err(|error| format!("configure V2 write timeout: {error}"))?;
    socket
        .set_nodelay(true)
        .map_err(|error| format!("configure V2 TCP_NODELAY: {error}"))?;

    let expected = decode_fingerprint(&endpoint.tls_certificate_sha256)?;
    let provider = Arc::new(crypto::ring::default_provider());
    let verifier = Arc::new(PinnedCertificateVerifier {
        expected,
        algorithms: provider.signature_verification_algorithms,
    });
    let config = ClientConfig::builder_with_provider(provider)
        .with_protocol_versions(&[&rustls::version::TLS13, &rustls::version::TLS12])
        .map_err(|error| format!("configure TLS versions: {error}"))?
        .dangerous()
        .with_custom_certificate_verifier(verifier)
        .with_no_client_auth();
    let server_name = ServerName::try_from(endpoint.host.clone())
        .map_err(|_| "invalid TLS server name".to_owned())?;
    let mut connection = ClientConnection::new(Arc::new(config), server_name)
        .map_err(|error| format!("create TLS connection: {error}"))?;
    connection
        .complete_io(&mut socket)
        .map_err(|error| format!("TLS handshake failed: {error}"))?;
    let peer = connection
        .peer_certificates()
        .and_then(|certificates| certificates.first())
        .ok_or_else(|| "TLS peer provided no certificate".to_owned())?;
    let actual = Sha256::digest(peer.as_ref());
    if actual.as_slice() != expected {
        return Err("TLS certificate fingerprint did not match the paired server".into());
    }
    Ok(StreamOwned::new(connection, socket))
}

#[derive(Debug)]
struct PinnedCertificateVerifier {
    expected: [u8; 32],
    algorithms: WebPkiSupportedAlgorithms,
}

impl ServerCertVerifier for PinnedCertificateVerifier {
    fn verify_server_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp_response: &[u8],
        _now: UnixTime,
    ) -> Result<ServerCertVerified, rustls::Error> {
        let actual = Sha256::digest(end_entity.as_ref());
        if actual.as_slice() == self.expected {
            Ok(ServerCertVerified::assertion())
        } else {
            Err(rustls::Error::InvalidCertificate(
                rustls::CertificateError::BadEncoding,
            ))
        }
    }

    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &rustls::DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        crypto::verify_tls12_signature(message, cert, dss, &self.algorithms)
    }

    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &rustls::DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        crypto::verify_tls13_signature(message, cert, dss, &self.algorithms)
    }

    fn supported_verify_schemes(&self) -> Vec<rustls::SignatureScheme> {
        self.algorithms.supported_schemes()
    }
}

fn write_message(
    stream: &mut TlsStream,
    kind: u16,
    body: &Value,
    minor: u16,
) -> Result<(), String> {
    let payload =
        serde_json::to_vec(body).map_err(|error| format!("serialize V2 message: {error}"))?;
    let limit = if matches!(kind, LIGHTING_FRAME | DEVICE_LIST | DEVICE_CHANGED) {
        MAX_MESSAGE
    } else {
        MAX_CONTROL_MESSAGE
    };
    if payload.len() > limit {
        return Err("V2 message exceeds the protocol size limit".into());
    }
    let mut header = [0u8; HEADER_SIZE];
    header[..4].copy_from_slice(MAGIC);
    header[4..6].copy_from_slice(&PROTOCOL_MAJOR.to_be_bytes());
    header[6..8].copy_from_slice(&minor.to_be_bytes());
    header[8..10].copy_from_slice(&kind.to_be_bytes());
    header[10..12].copy_from_slice(&0u16.to_be_bytes());
    header[12..16].copy_from_slice(&(payload.len() as u32).to_be_bytes());
    stream
        .write_all(&header)
        .and_then(|()| stream.write_all(&payload))
        .and_then(|()| stream.flush())
        .map_err(|error| format!("write V2 message: {error}"))
}

fn read_message_blocking(
    stream: &mut TlsStream,
    expected_minor: Option<u16>,
) -> Result<Message, String> {
    let mut header = [0u8; HEADER_SIZE];
    stream
        .read_exact(&mut header)
        .map_err(|error| format!("read V2 header: {error}"))?;
    let (minor, kind, length) = parse_header(&header, expected_minor)?;
    let mut payload = vec![0; length];
    stream
        .read_exact(&mut payload)
        .map_err(|error| format!("read V2 payload: {error}"))?;
    parse_message(minor, kind, &payload)
}

fn parse_header(header: &[u8], expected_minor: Option<u16>) -> Result<(u16, u16, usize), String> {
    if header.len() < HEADER_SIZE || &header[..4] != MAGIC {
        return Err("invalid V2 frame magic".into());
    }
    let major = u16::from_be_bytes([header[4], header[5]]);
    let minor = u16::from_be_bytes([header[6], header[7]]);
    let kind = u16::from_be_bytes([header[8], header[9]]);
    let flags = u16::from_be_bytes([header[10], header[11]]);
    let length = u32::from_be_bytes(header[12..16].try_into().unwrap_or([0; 4])) as usize;
    if major != PROTOCOL_MAJOR
        || minor < MIN_SUPPORTED_MINOR
        || minor > MAX_SUPPORTED_MINOR
        || expected_minor.is_some_and(|expected| expected != minor)
        || flags != 0
    {
        return Err("unsupported V2 message header".into());
    }
    let limit = if matches!(kind, LIGHTING_FRAME | DEVICE_LIST | DEVICE_CHANGED) {
        MAX_MESSAGE
    } else {
        MAX_CONTROL_MESSAGE
    };
    if length > limit {
        return Err("V2 message exceeds the protocol size limit".into());
    }
    Ok((minor, kind, length))
}

fn parse_message(minor: u16, kind: u16, payload: &[u8]) -> Result<Message, String> {
    let body =
        serde_json::from_slice(payload).map_err(|error| format!("invalid V2 JSON: {error}"))?;
    Ok(Message { minor, kind, body })
}

fn auth_proof(
    key: &[u8; 32],
    credential_type: &str,
    credential_id: &str,
    server_id: &str,
    client_id: &str,
    server_nonce: &[u8; 32],
    client_nonce: &[u8; 32],
    minor: u16,
) -> [u8; 32] {
    let mut mac = HmacSha256::new_from_slice(key).expect("HMAC accepts a 32-byte key");
    mac.update(b"MineLights Protocol V2\0");
    mac.update(credential_type.as_bytes());
    mac.update(b"\0");
    mac.update(credential_id.as_bytes());
    mac.update(b"\0");
    mac.update(server_id.as_bytes());
    mac.update(b"\0");
    mac.update(client_id.as_bytes());
    mac.update(server_nonce);
    mac.update(client_nonce);
    mac.update(&PROTOCOL_MAJOR.to_be_bytes());
    mac.update(&minor.to_be_bytes());
    mac.finalize().into_bytes().into()
}

fn build_layouts(server_id: &str, snapshot: DeviceList) -> Vec<V2Device> {
    let _revision = snapshot.revision;
    snapshot
        .devices
        .into_iter()
        .filter_map(|device| {
            let device_id = device.device_id;
            let mut layout = DeviceLayout {
                unique_id: format!("{}|{}", server_id, device_id),
                display_name: if device.vendor.is_empty() {
                    device.name
                } else {
                    format!("{} ({})", device.name, device.vendor)
                },
                integration: device.integration,
                ..DeviceLayout::default()
            };
            let _capabilities = device.capabilities;
            for element in device.elements {
                layout.leds.push(element.element_id);
                if let Some(key) = element.key.or(element.zone) {
                    let key = super::standardize_key(&key);
                    layout.keys.entry(key).or_default().push(element.element_id);
                }
            }
            (!layout.leds.is_empty()).then_some(V2Device {
                device_id,
                wire_elements: layout.leds.clone(),
                wire_keys: layout.keys.clone(),
                layout,
            })
        })
        .collect()
}

fn new_identity() -> Result<ClientIdentity, String> {
    let mut secret = [0u8; 32];
    getrandom::fill(&mut secret).map_err(|error| format!("secure random unavailable: {error}"))?;
    let mut id_bytes = [0u8; 16];
    getrandom::fill(&mut id_bytes)
        .map_err(|error| format!("secure random unavailable: {error}"))?;
    id_bytes[6] = (id_bytes[6] & 0x0f) | 0x40;
    id_bytes[8] = (id_bytes[8] & 0x3f) | 0x80;
    let client_id = format_uuid(id_bytes);
    Ok(ClientIdentity {
        client_id,
        client_secret: URL_SAFE_NO_PAD.encode(secret),
    })
}

fn load_store(config_dir: &Path) -> Result<ClientStore, String> {
    let path = client_store_path(config_dir);
    if !path.is_file() {
        return Ok(ClientStore::default());
    }
    let bytes = read_bounded(&path, 256 * 1024)?;
    let store: ClientStore = serde_json::from_slice(&bytes)
        .map_err(|error| format!("invalid MineLights V2 client state: {error}"))?;
    if let Some(identity) = &store.local {
        validate_uuid(&identity.client_id)?;
        decode_secret(&identity.client_secret)?;
    }
    if store.remotes.len() > 1022 {
        return Err("MineLights V2 client state contains too many remote servers".into());
    }
    let mut server_ids = HashSet::new();
    for remote in &store.remotes {
        validate_endpoint(&remote.endpoint)?;
        validate_uuid(&remote.client_id)?;
        decode_secret(&remote.client_secret)?;
        if !server_ids.insert(&remote.endpoint.server_id) {
            return Err("MineLights V2 client state contains a duplicate remote server".into());
        }
    }
    Ok(store)
}

fn save_store(config_dir: &Path, store: &ClientStore) -> Result<(), String> {
    let path = client_store_path(config_dir);
    let parent = path
        .parent()
        .ok_or_else(|| "V2 client-state path has no parent".to_owned())?;
    fs::create_dir_all(parent)
        .map_err(|error| format!("create V2 client-state directory: {error}"))?;
    let bytes =
        serde_json::to_vec(store).map_err(|error| format!("serialize V2 client state: {error}"))?;
    let nonce = format_uuid({
        let mut bytes = [0u8; 16];
        getrandom::fill(&mut bytes)
            .map_err(|error| format!("secure random unavailable: {error}"))?;
        bytes
    });
    let temp = path.with_file_name(format!("protocol-v2-clients-{nonce}.tmp"));
    let mut options = fs::OpenOptions::new();
    options.create_new(true).write(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let file = options
        .open(&temp)
        .map_err(|error| format!("create protected V2 client state: {error}"))?;
    drop(file);
    #[cfg(windows)]
    protect_windows_file(&temp)?;
    let mut file = fs::OpenOptions::new()
        .write(true)
        .truncate(true)
        .open(&temp)
        .map_err(|error| format!("open protected V2 client state: {error}"))?;
    file.write_all(&bytes)
        .and_then(|()| file.sync_all())
        .map_err(|error| format!("write V2 client state: {error}"))?;
    drop(file);
    replace_client_store(&temp, &path)?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&path, fs::Permissions::from_mode(0o600))
            .map_err(|error| format!("protect V2 client state: {error}"))?;
    }
    Ok(())
}

fn replace_client_store(temp: &Path, destination: &Path) -> Result<(), String> {
    fs::rename(temp, destination).map_err(|error| format!("install V2 client state: {error}"))
}

#[cfg(windows)]
fn protect_windows_file(path: &Path) -> Result<(), String> {
    use std::{
        os::windows::process::CommandExt,
        process::{Command, Stdio},
    };
    let user =
        std::env::var("USERNAME").map_err(|_| "Windows account name is unavailable".to_owned())?;
    let domain = std::env::var("USERDOMAIN").unwrap_or_else(|_| ".".into());
    let account = format!("{domain}\\{user}:(F)");
    let status = Command::new("icacls.exe")
        .arg(path)
        .arg("/inheritance:r")
        .arg("/grant:r")
        .arg(account)
        .arg("*S-1-5-18:(F)")
        .arg("*S-1-5-32-544:(F)")
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .creation_flags(0x0800_0000)
        .status()
        .map_err(|error| format!("protect V2 client credentials with Windows ACL: {error}"))?;
    if !status.success() {
        return Err("Windows rejected the V2 credential file permissions".into());
    }
    Ok(())
}

fn client_store_path(config_dir: &Path) -> PathBuf {
    config_dir
        .join("MineLights")
        .join("protocol-v2-clients.json")
}

fn local_data_dir() -> Option<PathBuf> {
    if let Some(path) = std::env::var_os("LOCALAPPDATA") {
        return Some(PathBuf::from(path).join("MineLights").join("v2"));
    }
    if let Some(path) = std::env::var_os("XDG_DATA_HOME") {
        return Some(PathBuf::from(path).join("MineLights").join("v2"));
    }
    std::env::var_os("HOME").map(PathBuf::from).map(|home| {
        home.join(".local")
            .join("share")
            .join("MineLights")
            .join("v2")
    })
}

fn read_bounded(path: &Path, max: usize) -> Result<Vec<u8>, String> {
    let mut file =
        fs::File::open(path).map_err(|error| format!("open {}: {error}", path.display()))?;
    let mut bytes = Vec::with_capacity(max.min(16 * 1024));
    Read::by_ref(&mut file)
        .take(max as u64 + 1)
        .read_to_end(&mut bytes)
        .map_err(|error| format!("read {}: {error}", path.display()))?;
    if bytes.len() > max {
        return Err(format!("{} exceeds the size limit", path.display()));
    }
    Ok(bytes)
}

fn validate_uuid(value: &str) -> Result<(), String> {
    if value.len() != 36
        || !value.bytes().enumerate().all(|(index, byte)| match index {
            8 | 13 | 18 | 23 => byte == b'-',
            _ => byte.is_ascii_hexdigit() && !byte.is_ascii_uppercase(),
        })
    {
        return Err("invalid lowercase UUID".into());
    }
    Ok(())
}

fn format_uuid(bytes: [u8; 16]) -> String {
    let hex = hex::encode(bytes);
    format!(
        "{}-{}-{}-{}-{}",
        &hex[..8],
        &hex[8..12],
        &hex[12..16],
        &hex[16..20],
        &hex[20..]
    )
}

fn random_request_id() -> Result<String, String> {
    let mut bytes = [0u8; 16];
    getrandom::fill(&mut bytes).map_err(|error| format!("secure random unavailable: {error}"))?;
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    Ok(format_uuid(bytes))
}

fn decode_secret(encoded: &str) -> Result<[u8; 32], String> {
    decode_base64_32(encoded)
}

fn decode_base64_32(encoded: &str) -> Result<[u8; 32], String> {
    let bytes = URL_SAFE_NO_PAD
        .decode(encoded)
        .map_err(|_| "invalid base64url credential".to_owned())?;
    bytes
        .try_into()
        .map_err(|_| "credential must contain exactly 32 bytes".to_owned())
}

fn validate_fingerprint(value: &str) -> Result<(), String> {
    if value.len() != 64
        || !value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
    {
        return Err("TLS certificate fingerprint must be 64 lowercase hex characters".into());
    }
    Ok(())
}

fn decode_fingerprint(value: &str) -> Result<[u8; 32], String> {
    validate_fingerprint(value)?;
    let bytes = hex::decode(value).map_err(|_| "invalid TLS certificate fingerprint".to_owned())?;
    bytes
        .try_into()
        .map_err(|_| "invalid TLS certificate fingerprint".to_owned())
}

fn validate_endpoint(endpoint: &Endpoint) -> Result<(), String> {
    if endpoint.port == 0
        || endpoint.host.is_empty()
        || endpoint.host.len() > 253
        || endpoint.host.chars().any(char::is_whitespace)
    {
        return Err("server endpoint is incomplete".into());
    }
    validate_uuid(&endpoint.server_id)?;
    validate_fingerprint(&endpoint.tls_certificate_sha256)
}

fn parse_utc_expiry(value: &str) -> Result<std::time::SystemTime, String> {
    let invalid = || "pairing expiry must be RFC 3339 UTC".to_owned();
    if !value.ends_with('Z') || value.len() < 20 {
        return Err(invalid());
    }
    let core = &value[..value.len() - 1];
    let (date, time) = core.split_once('T').ok_or_else(invalid)?;
    if date.len() != 10 {
        return Err(invalid());
    }
    let mut date = date.split('-');
    let year: i64 = date
        .next()
        .ok_or_else(invalid)?
        .parse()
        .map_err(|_| invalid())?;
    let month: u32 = date
        .next()
        .ok_or_else(invalid)?
        .parse()
        .map_err(|_| invalid())?;
    let day: u32 = date
        .next()
        .ok_or_else(invalid)?
        .parse()
        .map_err(|_| invalid())?;
    if date.next().is_some() || !(1..=12).contains(&month) || !(1..=31).contains(&day) {
        return Err(invalid());
    }
    let (clock, fraction) = if let Some((clock, fraction)) = time.split_once('.') {
        if fraction.is_empty() || !fraction.bytes().all(|byte| byte.is_ascii_digit()) {
            return Err(invalid());
        }
        (clock, fraction)
    } else {
        (time, "")
    };
    if clock.len() != 8 || (time.contains('.') && fraction.is_empty()) {
        return Err(invalid());
    }
    let mut clock = clock.split(':');
    let hour: u32 = clock
        .next()
        .ok_or_else(invalid)?
        .parse()
        .map_err(|_| invalid())?;
    let minute: u32 = clock
        .next()
        .ok_or_else(invalid)?
        .parse()
        .map_err(|_| invalid())?;
    let second: u32 = clock
        .next()
        .ok_or_else(invalid)?
        .parse()
        .map_err(|_| invalid())?;
    if clock.next().is_some() || hour > 23 || minute > 59 || second > 59 {
        return Err(invalid());
    }
    let max_day = match month {
        1 | 3 | 5 | 7 | 8 | 10 | 12 => 31,
        4 | 6 | 9 | 11 => 30,
        2 if year % 4 == 0 && (year % 100 != 0 || year % 400 == 0) => 29,
        2 => 28,
        _ => return Err(invalid()),
    };
    if day > max_day {
        return Err(invalid());
    }
    let days = days_from_civil(year, month, day);
    let seconds = days
        .checked_mul(86_400)
        .and_then(|value| value.checked_add(i64::from(hour * 3600 + minute * 60 + second)))
        .ok_or_else(invalid)?;
    if seconds < 0 {
        return Err(invalid());
    }
    Ok(std::time::UNIX_EPOCH + Duration::from_secs(seconds as u64))
}

fn days_from_civil(year: i64, month: u32, day: u32) -> i64 {
    let year = year - i64::from(month <= 2);
    let era = year.div_euclid(400);
    let year_of_era = year - era * 400;
    let adjusted_month = i64::from(month) + if month > 2 { -3 } else { 9 };
    let day_of_year = (153 * adjusted_month + 2) / 5 + i64::from(day) - 1;
    let day_of_era = year_of_era * 365 + year_of_era / 4 - year_of_era / 100 + day_of_year;
    era * 146_097 + day_of_era - 719_468
}
