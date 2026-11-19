use crate::minelights_protocol::{Endpoint, RemoteServer, connect_remote_control};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use hmac::{Hmac, Mac};
use rustls::pki_types::{CertificateDer, PrivatePkcs8KeyDer};
use rustls::{ServerConfig, ServerConnection, StreamOwned};
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::io::{Read, Write};
use std::net::{Ipv4Addr, TcpListener, TcpStream};
use std::path::PathBuf;
use std::process::Command;
use std::sync::Arc;
use std::time::{Duration, Instant};

const SERVER_ID: &str = "1f299320-608d-4b23-8f6f-9d09e90db4dd";
const CLIENT_ID: &str = "29242c54-c946-4ee6-8d8d-715a8fcedea2";
const TEST_SECRET: [u8; 32] = [0x2a; 32];
const TEST_NONCE: [u8; 32] = [0x23; 32];
const TIMEOUT: Duration = Duration::from_secs(8);

type Tls = StreamOwned<ServerConnection, TcpStream>;

struct CertificateFiles(PathBuf);

impl CertificateFiles {
    fn generate() -> Self {
        let dir =
            std::env::temp_dir().join(format!("minelights-v2-fixture-{}", std::process::id()));
        std::fs::create_dir(&dir).unwrap();
        let files = Self(dir);
        files.openssl(&[
            "req",
            "-x509",
            "-newkey",
            "ec",
            "-pkeyopt",
            "ec_paramgen_curve:P-256",
            "-nodes",
            "-subj",
            "/CN=MineLights V2 loopback fixture",
            "-days",
            "1",
            "-keyout",
            "key.pem",
            "-outform",
            "DER",
            "-out",
            "certificate.der",
        ]);
        files.openssl(&[
            "pkcs8", "-topk8", "-nocrypt", "-in", "key.pem", "-outform", "DER", "-out", "key.der",
        ]);
        files
    }

    fn openssl(&self, args: &[&str]) {
        let openssl =
            std::env::var_os("MINELIGHTS_TEST_OPENSSL").unwrap_or_else(|| "openssl".into());
        let output = Command::new(openssl)
            .current_dir(&self.0)
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "OpenSSL fixture setup failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
    }

    fn config(&self) -> (Arc<ServerConfig>, String) {
        let cert = std::fs::read(self.0.join("certificate.der")).unwrap();
        let key = std::fs::read(self.0.join("key.der")).unwrap();
        let fingerprint = hex::encode(Sha256::digest(&cert));
        let config =
            ServerConfig::builder_with_provider(rustls::crypto::ring::default_provider().into())
                .with_safe_default_protocol_versions()
                .unwrap()
                .with_no_client_auth()
                .with_single_cert(
                    vec![CertificateDer::from(cert)],
                    PrivatePkcs8KeyDer::from(key).into(),
                )
                .unwrap();
        (Arc::new(config), fingerprint)
    }
}

impl Drop for CertificateFiles {
    fn drop(&mut self) {
        for name in ["certificate.der", "key.pem", "key.der"] {
            let _ = std::fs::remove_file(self.0.join(name));
        }
        let _ = std::fs::remove_dir(&self.0);
    }
}

fn open_peer(listener: &TcpListener, config: &Arc<ServerConfig>) -> Tls {
    let deadline = Instant::now() + TIMEOUT;
    let socket = loop {
        match listener.accept() {
            Ok((socket, address)) => {
                assert!(address.ip().is_loopback());
                break socket;
            }
            Err(error)
                if error.kind() == std::io::ErrorKind::WouldBlock && Instant::now() < deadline =>
            {
                std::thread::sleep(Duration::from_millis(10));
            }
            Err(error) => panic!("accept V2 peer: {error}"),
        }
    };
    socket.set_nonblocking(false).unwrap();
    socket.set_read_timeout(Some(TIMEOUT)).unwrap();
    socket.set_write_timeout(Some(TIMEOUT)).unwrap();
    StreamOwned::new(ServerConnection::new(config.clone()).unwrap(), socket)
}

fn read(peer: &mut Tls, message_type: u16) -> Value {
    let mut header = [0u8; 16];
    peer.read_exact(&mut header).unwrap();
    assert_eq!(&header[..8], b"MLV2\0\x02\0\0");
    assert_eq!(
        u16::from_be_bytes(header[8..10].try_into().unwrap()),
        message_type
    );
    assert_eq!(&header[10..12], &[0, 0]);
    let length = u32::from_be_bytes(header[12..16].try_into().unwrap()) as usize;
    assert!(length <= 16 * 1024);
    let mut bytes = vec![0; length];
    peer.read_exact(&mut bytes).unwrap();
    serde_json::from_slice(&bytes).unwrap()
}

fn send(peer: &mut Tls, message_type: u16, body: Value) {
    let bytes = serde_json::to_vec(&body).unwrap();
    peer.write_all(b"MLV2\0\x02\0\0").unwrap();
    peer.write_all(&message_type.to_be_bytes()).unwrap();
    peer.write_all(&0u16.to_be_bytes()).unwrap();
    peer.write_all(&(bytes.len() as u32).to_be_bytes()).unwrap();
    peer.write_all(&bytes).unwrap();
    peer.flush().unwrap();
}

fn authenticate(peer: &mut Tls, accepted: bool) {
    let hello = read(peer, 1);
    assert_eq!(hello["client_id"], CLIENT_ID);
    assert_eq!(hello["app_version"], "2.4.1");
    send(
        peer,
        2,
        json!({"server_id":SERVER_ID,"server_name":"fixture",
        "selected_minor":0,"nonce":URL_SAFE_NO_PAD.encode(TEST_NONCE)}),
    );
    let auth = read(peer, 3);
    assert_eq!(auth["credential_type"], "client_secret");
    assert_eq!(auth["credential_id"], CLIENT_ID);
    let client_nonce = URL_SAFE_NO_PAD
        .decode(auth["client_nonce"].as_str().unwrap())
        .unwrap();
    assert_eq!(client_nonce.len(), 32);
    let proof = URL_SAFE_NO_PAD
        .decode(auth["proof"].as_str().unwrap())
        .unwrap();
    let mut mac = Hmac::<Sha256>::new_from_slice(&TEST_SECRET).unwrap();
    mac.update(
        format!("MineLights Protocol V2\0client_secret\0{CLIENT_ID}\0{SERVER_ID}\0{CLIENT_ID}")
            .as_bytes(),
    );
    mac.update(&TEST_NONCE);
    mac.update(&client_nonce);
    mac.update(&[0, 2, 0, 0]);
    assert_eq!(mac.verify_slice(&proof).is_ok(), accepted);
    let response = if accepted {
        json!({"ok":true,"scopes":["devices.read","lighting.write"]})
    } else {
        json!({"ok":false,"error_code":"BAD_PROOF"})
    };
    send(peer, 4, response);
}

fn device_list(peer: &mut Tls, revision: u64) {
    send(
        peer,
        0x10,
        json!({"revision":revision,"devices":[{
            "device_id":"fixture-device","name":"Fixture keyboard","integration":"Corsair",
            "elements":[{"element_id":7,"key":"Left Control"}]
        }]}),
    );
}

#[test]
#[ignore = "generates an ephemeral fixture certificate using OpenSSL"]
fn authenticated_v2_tls_rejects_bad_identity_and_reconnects() {
    crate::install_pinned_tls(crate::pinned_tls::connect);
    let files = CertificateFiles::generate();
    let (config, fingerprint) = files.config();
    let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
    listener.set_nonblocking(true).unwrap();
    let port = listener.local_addr().unwrap().port();
    let peer = std::thread::spawn(move || {
        let mut pin_mismatch = open_peer(&listener, &config);
        assert!(pin_mismatch.read_exact(&mut [0]).is_err());
        let mut password_mismatch = open_peer(&listener, &config);
        authenticate(&mut password_mismatch, false);

        let mut first = open_peer(&listener, &config);
        authenticate(&mut first, true);
        device_list(&mut first, 1);
        drop(first);

        let mut second = open_peer(&listener, &config);
        authenticate(&mut second, true);
        device_list(&mut second, 2);
        assert_eq!(read(&mut second, 0xff)["reason"], "client_disconnect");
    });

    let mut remote = RemoteServer {
        endpoint: Endpoint {
            server_id: SERVER_ID.into(),
            server_name: "fixture".into(),
            host: "127.0.0.1".into(),
            port,
            tls_certificate_sha256: "00".repeat(32),
        },
        client_id: CLIENT_ID.into(),
        client_secret: URL_SAFE_NO_PAD.encode(TEST_SECRET),
    };
    assert!(
        connect_remote_control(&remote)
            .err()
            .unwrap()
            .contains("TLS")
    );
    remote.endpoint.tls_certificate_sha256 = fingerprint;
    remote.client_secret = URL_SAFE_NO_PAD.encode([0; 32]);
    assert_eq!(
        connect_remote_control(&remote).err().as_deref(),
        Some("BAD_PROOF")
    );

    remote.client_secret = URL_SAFE_NO_PAD.encode(TEST_SECRET);
    let (mut lost, devices) = connect_remote_control(&remote).unwrap();
    assert_eq!(lost.server_id(), SERVER_ID);
    assert_eq!(devices[0].layout.keys["LCTRL"], vec![7]);
    let deadline = Instant::now() + TIMEOUT;
    loop {
        if lost.poll().is_err() {
            break;
        }
        assert!(
            Instant::now() < deadline,
            "lost V2 session was not detected"
        );
        std::thread::sleep(Duration::from_millis(10));
    }
    drop(lost);

    let (reconnected, devices) = connect_remote_control(&remote).unwrap();
    assert_eq!(
        devices[0].layout.unique_id,
        format!("{SERVER_ID}|fixture-device")
    );
    drop(reconnected);
    peer.join().unwrap();
}
