use std::{
    collections::{HashMap, HashSet},
    io::{BufRead, BufReader, Read, Write},
    net::{SocketAddr, TcpStream, ToSocketAddrs, UdpSocket},
    thread,
    time::{Duration, Instant},
};

const OPENRGB_LED_BASE: u32 = 0x8000_0000;
const YEELIGHT_LED_BASE: u32 = 0xF000_0000;
const OPENRGB_ENDPOINT_STRIDE: u32 = 0x0100_0000;
const OPENRGB_DEVICE_STRIDE: u32 = 0x0001_0000;
const MAX_OPENRGB_ENDPOINTS: usize =
    ((YEELIGHT_LED_BASE - OPENRGB_LED_BASE) / OPENRGB_ENDPOINT_STRIDE) as usize;
const YEELIGHT_MULTICAST: &str = "239.255.255.250:1982";
const YEELIGHT_DISCOVERY: &[u8] = b"M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1982\r\nMAN: \"ssdp:discover\"\r\nST: wifi_bulb\r\n\r\n";

#[derive(Clone, Debug)]
pub struct DirectLayout {
    pub unique_id: String,
    pub display_name: String,
    pub integration: String,
    pub keys: HashMap<String, Vec<u32>>,
    pub leds: Vec<u32>,
}

#[derive(Default)]
pub struct DirectOutputs {
    openrgb: Vec<OpenRgbClient>,
    yeelights: Vec<YeelightDevice>,
}

impl DirectOutputs {
    pub fn configure_openrgb(&mut self, endpoints: &[(String, u16)]) {
        let mut previous = std::mem::take(&mut self.openrgb);
        let mut next = Vec::new();
        for (slot, endpoint) in endpoints.iter().take(MAX_OPENRGB_ENDPOINTS).enumerate() {
            if endpoint.0.is_empty()
                || endpoint.0.len() > 253
                || endpoint.0.chars().any(char::is_whitespace)
                || endpoint.1 == 0
            {
                continue;
            }
            if let Some(index) = previous.iter().position(|client| {
                client.endpoint_host == endpoint.0
                    && client.endpoint_port == endpoint.1
                    && client.endpoint_slot == slot
            }) {
                next.push(previous.swap_remove(index));
            } else if let Ok(client) = OpenRgbClient::connect(endpoint.0.clone(), endpoint.1, slot)
            {
                next.push(client);
            }
        }
        self.openrgb = next;
    }

    pub fn disconnect_openrgb(&mut self) {
        self.openrgb.clear();
    }

    pub fn discover_yeelights(&mut self) {
        self.disconnect_yeelights();
        self.yeelights = YeelightDevice::discover();
    }

    pub fn disconnect_yeelights(&mut self) {
        for device in &mut self.yeelights {
            device.disconnect();
        }
        self.yeelights.clear();
    }

    pub fn layouts(&self) -> Vec<DirectLayout> {
        let mut result = self
            .openrgb
            .iter()
            .flat_map(OpenRgbClient::layouts)
            .collect::<Vec<_>>();
        result.extend(self.yeelights.iter().enumerate().map(|(index, device)| {
            let led = YEELIGHT_LED_BASE.saturating_add(index as u32);
            let display_name = if device.name.is_empty() {
                device.id.clone()
            } else {
                device.name.clone()
            };
            DirectLayout {
                unique_id: format!("Yeelight|{display_name}"),
                display_name: format!("Yeelight {display_name}"),
                integration: "Yeelight".into(),
                keys: HashMap::from([(format!("YEELIGHT_{index}"), vec![led])]),
                leds: vec![led],
            }
        }));
        result
    }

    pub fn update(&mut self, frame: &HashMap<u32, [u8; 3]>) {
        self.openrgb.retain_mut(|client| client.update(frame));
        for (index, device) in self.yeelights.iter_mut().enumerate() {
            let color = frame
                .get(&YEELIGHT_LED_BASE.saturating_add(index as u32))
                .copied()
                .unwrap_or([0, 0, 0]);
            device.set_color(color);
        }
    }
}

struct OpenRgbDevice {
    controller_id: u32,
    led_count: usize,
    display_name: String,
    key_leds: Vec<(usize, String)>,
}

struct OpenRgbClient {
    endpoint_host: String,
    endpoint_port: u16,
    endpoint_slot: usize,
    stream: TcpStream,
    devices: Vec<OpenRgbDevice>,
}

impl OpenRgbClient {
    fn connect(
        endpoint_host: String,
        endpoint_port: u16,
        endpoint_slot: usize,
    ) -> Result<Self, String> {
        let addresses = (endpoint_host.as_str(), endpoint_port)
            .to_socket_addrs()
            .map_err(|e| format!("OpenRGB address: {e}"))?
            .collect::<Vec<_>>();
        let mut stream = None;
        let mut last_error = None;
        for address in addresses {
            match TcpStream::connect_timeout(&address, Duration::from_secs(1)) {
                Ok(connected) => {
                    stream = Some(connected);
                    break;
                }
                Err(error) => last_error = Some(error),
            }
        }
        let mut stream = stream.ok_or_else(|| {
            last_error.map_or_else(
                || "OpenRGB endpoint resolved to no addresses".to_owned(),
                |error| error.to_string(),
            )
        })?;
        let _ = stream.set_read_timeout(Some(Duration::from_secs(2)));
        let _ = stream.set_write_timeout(Some(Duration::from_secs(2)));
        let client_name = b"MineLights\0";
        send_packet(&mut stream, 0, 50, client_name)?;
        send_packet(&mut stream, 0, 40, &5u32.to_le_bytes())?;
        let version_data = read_response(&mut stream)?;
        let server_version = read_u32_at(&version_data, 0)?;
        let protocol_version = server_version.min(5);

        send_packet(&mut stream, 0, 0, &[])?;
        let controller_data = read_response(&mut stream)?;
        let controller_count = read_u32_at(&controller_data, 0)? as usize;
        if controller_count > 4096 {
            return Err("OpenRGB returned an unreasonable controller count".into());
        }
        let has_ids = server_version >= 6 && controller_data.len() >= 4 + controller_count * 4;
        let mut cursor = 4;
        let mut devices = Vec::with_capacity(controller_count);
        for index in 0..controller_count {
            let controller_id = if has_ids {
                let id = read_u32_at(&controller_data, cursor)?;
                cursor += 4;
                id
            } else {
                index as u32
            };
            send_packet(
                &mut stream,
                controller_id,
                1,
                &protocol_version.to_le_bytes(),
            )?;
            let info = read_response(&mut stream)?;
            let (display_name, led_count, key_leds) =
                parse_openrgb_device(&info, protocol_version)?;
            send_packet(&mut stream, controller_id, 1100, &[])?;
            devices.push(OpenRgbDevice {
                controller_id,
                led_count,
                display_name,
                key_leds,
            });
        }
        Ok(Self {
            endpoint_host,
            endpoint_port,
            endpoint_slot,
            stream,
            devices,
        })
    }

    fn layouts(&self) -> Vec<DirectLayout> {
        self.devices
            .iter()
            .enumerate()
            .filter_map(|(index, device)| {
                if index >= 256 {
                    return None;
                }
                let offset = OPENRGB_LED_BASE
                    .saturating_add(
                        (self.endpoint_slot as u32).saturating_mul(OPENRGB_ENDPOINT_STRIDE),
                    )
                    .saturating_add((index as u32).saturating_mul(OPENRGB_DEVICE_STRIDE));
                let mut keys: HashMap<String, Vec<u32>> = HashMap::new();
                let mut leds = Vec::new();
                for (local_id, name) in &device.key_leds {
                    let id = offset.saturating_add(*local_id as u32);
                    keys.entry(standardize_key(name)).or_default().push(id);
                    if !leds.contains(&id) {
                        leds.push(id);
                    }
                }
                if leds.is_empty() {
                    return None;
                }
                Some(DirectLayout {
                    unique_id: format!(
                        "OpenRGB|{}:{}|{}|{}",
                        self.endpoint_host,
                        self.endpoint_port,
                        device.controller_id,
                        device.display_name
                    ),
                    display_name: device.display_name.clone(),
                    integration: "OpenRGB".into(),
                    keys,
                    leds,
                })
            })
            .collect()
    }

    fn update(&mut self, frame: &HashMap<u32, [u8; 3]>) -> bool {
        for (device_index, device) in self.devices.iter().enumerate() {
            if device.led_count == 0 || device.key_leds.is_empty() {
                continue;
            }
            if device_index >= 256 {
                continue;
            }
            let offset = OPENRGB_LED_BASE
                .saturating_add((self.endpoint_slot as u32).saturating_mul(OPENRGB_ENDPOINT_STRIDE))
                .saturating_add((device_index as u32).saturating_mul(OPENRGB_DEVICE_STRIDE));
            let colors: Vec<[u8; 3]> = device
                .key_leds
                .iter()
                .filter_map(|(index, _)| frame.get(&offset.saturating_add(*index as u32)).copied())
                .collect();
            if colors.is_empty() {
                continue;
            }
            let led_count = device.led_count.min(u16::MAX as usize);
            let mut payload = Vec::with_capacity(6 + led_count * 4);
            payload.extend_from_slice(&((6 + led_count * 4) as u32).to_le_bytes());
            payload.extend_from_slice(&(led_count as u16).to_le_bytes());
            let mut output = vec![[0u8; 4]; led_count];
            for (index, led) in output.iter_mut().enumerate() {
                let color = colors[index % colors.len()];
                *led = [color[0], color[1], color[2], 255];
            }
            for (local_id, _) in &device.key_leds {
                if *local_id < led_count {
                    if let Some(color) = frame.get(&offset.saturating_add(*local_id as u32)) {
                        output[*local_id] = [color[0], color[1], color[2], 255];
                    }
                }
            }
            for led in output {
                payload.extend_from_slice(&led);
            }
            if send_packet(&mut self.stream, device.controller_id, 1050, &payload).is_err() {
                return false;
            }
        }
        true
    }
}

struct Cursor<'a> {
    bytes: &'a [u8],
    offset: usize,
}

impl<'a> Cursor<'a> {
    fn new(bytes: &'a [u8]) -> Self {
        Self { bytes, offset: 0 }
    }
    fn take(&mut self, count: usize) -> Result<&'a [u8], String> {
        let end = self
            .offset
            .checked_add(count)
            .ok_or("OpenRGB packet length overflow")?;
        let result = self
            .bytes
            .get(self.offset..end)
            .ok_or("OpenRGB returned a truncated device packet")?;
        self.offset = end;
        Ok(result)
    }
    fn u16(&mut self) -> Result<u16, String> {
        Ok(u16::from_le_bytes(self.take(2)?.try_into().unwrap()))
    }
    fn u32(&mut self) -> Result<u32, String> {
        Ok(u32::from_le_bytes(self.take(4)?.try_into().unwrap()))
    }
    fn string(&mut self) -> Result<String, String> {
        let len = usize::from(self.u16()?);
        if len == 0 {
            return Ok(String::new());
        }
        let bytes = self.take(len)?;
        let text = bytes.strip_suffix(&[0]).unwrap_or(bytes);
        Ok(String::from_utf8_lossy(text).into_owned())
    }
}

fn parse_openrgb_device(
    bytes: &[u8],
    protocol: u32,
) -> Result<(String, usize, Vec<(usize, String)>), String> {
    let mut cursor = Cursor::new(bytes);
    let _device_type = cursor.u32()?;
    let _device_index = cursor.u32()?;
    let name = cursor.string()?;
    if protocol >= 1 {
        let _ = cursor.string()?;
    }
    for _ in 0..4 {
        let _ = cursor.string()?;
    }
    let mode_count = usize::from(cursor.u16()?);
    let _ = cursor.u32()?;
    for _ in 0..mode_count {
        let _ = cursor.string()?;
        for _ in 0..4 {
            let _ = cursor.u32()?;
        }
        if protocol >= 3 {
            for _ in 0..2 {
                let _ = cursor.u32()?;
            }
        }
        for _ in 0..3 {
            let _ = cursor.u32()?;
        }
        if protocol >= 3 {
            let _ = cursor.u32()?;
        }
        for _ in 0..2 {
            let _ = cursor.u32()?;
        }
        let color_count = usize::from(cursor.u16()?);
        let _ = cursor.take(color_count.saturating_mul(4))?;
    }
    let zone_count = usize::from(cursor.u16()?);
    let mut total_leds = 0usize;
    for _ in 0..zone_count {
        let _ = cursor.string()?;
        for _ in 0..3 {
            let _ = cursor.u32()?;
        }
        total_leds = total_leds.saturating_add(cursor.u32()? as usize);
        let matrix_len = usize::from(cursor.u16()?);
        let _ = cursor.take(matrix_len)?;
        if protocol >= 4 {
            let segment_count = usize::from(cursor.u16()?);
            for _ in 0..segment_count {
                let _ = cursor.string()?;
                for _ in 0..3 {
                    let _ = cursor.u32()?;
                }
            }
        }
        if protocol >= 5 {
            let _ = cursor.u32()?;
        }
    }
    let led_count = usize::from(cursor.u16()?);
    let mut key_leds = Vec::new();
    for index in 0..led_count {
        let led_name = cursor.string()?;
        if !led_name.is_empty() {
            key_leds.push((index, led_name));
        }
        let _ = cursor.u32()?;
    }
    let color_count = usize::from(cursor.u16()?);
    let _ = cursor.take(color_count.saturating_mul(4))?;
    if protocol >= 5 {
        let alternate_names = usize::from(cursor.u16()?);
        for _ in 0..alternate_names {
            let _ = cursor.string()?;
        }
        let _ = cursor.u32()?;
    }
    Ok((name, total_leds.max(led_count), key_leds))
}

fn send_packet(
    stream: &mut TcpStream,
    device_id: u32,
    command: u32,
    payload: &[u8],
) -> Result<(), String> {
    let mut header = [0u8; 16];
    header[..4].copy_from_slice(b"ORGB");
    header[4..8].copy_from_slice(&device_id.to_le_bytes());
    header[8..12].copy_from_slice(&command.to_le_bytes());
    header[12..16].copy_from_slice(&(payload.len() as u32).to_le_bytes());
    stream
        .write_all(&header)
        .and_then(|()| stream.write_all(payload))
        .and_then(|()| stream.flush())
        .map_err(|e| e.to_string())
}

fn read_response(stream: &mut TcpStream) -> Result<Vec<u8>, String> {
    let mut header = [0u8; 16];
    stream.read_exact(&mut header).map_err(|e| e.to_string())?;
    if &header[..4] != b"ORGB" {
        return Err("OpenRGB returned an invalid packet signature".into());
    }
    let len = u32::from_le_bytes(header[12..16].try_into().unwrap()) as usize;
    if len > 64 * 1024 * 1024 {
        return Err("OpenRGB response exceeded the 64 MiB limit".into());
    }
    let mut payload = vec![0u8; len];
    stream.read_exact(&mut payload).map_err(|e| e.to_string())?;
    Ok(payload)
}

fn read_u32_at(bytes: &[u8], offset: usize) -> Result<u32, String> {
    let end = offset.checked_add(4).ok_or("OpenRGB offset overflow")?;
    let value = bytes
        .get(offset..end)
        .ok_or("OpenRGB response omitted a 32-bit value")?;
    Ok(u32::from_le_bytes(value.try_into().unwrap()))
}

fn standardize_key(raw: &str) -> String {
    let mut name = raw
        .trim()
        .to_ascii_uppercase()
        .replace("CONTROL", "CTRL")
        .replace("WINDOWS", "WIN");
    if let Some(value) = name.strip_prefix("KEY: ") {
        name = value.to_owned();
    }
    let suffix: String = name
        .chars()
        .rev()
        .take_while(|c| c.is_ascii_digit())
        .collect::<Vec<_>>()
        .into_iter()
        .rev()
        .collect();
    if name.contains("DRAM") || name.contains("CORSAIR PRO LED") || name.contains("RAM") {
        if name.contains("CORSAIR PRO LED") {
            if let Ok(index) = suffix.parse::<u32>() {
                return format!("RAM_{}", index.saturating_add(1));
            }
        }
        return if suffix.is_empty() {
            "RAM".into()
        } else {
            format!("RAM_{suffix}")
        };
    }
    if name.contains("LEDSTRIPE") || name.contains("UNDERGLOW") {
        return if suffix.is_empty() {
            "UNDERGLOW".into()
        } else {
            format!("UNDERGLOW_{suffix}")
        };
    }
    if name.contains("PROGRAMMABLE") {
        return format!("G{suffix}");
    }
    if name.contains("CUSTOM") || name.contains("WHEEL") {
        return if suffix.is_empty() {
            "WHEEL".into()
        } else {
            format!("WHEEL_{suffix}")
        };
    }
    if name.contains("PLAYER") {
        return if suffix.is_empty() {
            "PLAYER".into()
        } else {
            format!("PLAYER_{suffix}")
        };
    }
    name.split_whitespace()
        .collect::<Vec<_>>()
        .join("_")
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || *c == '_')
        .collect()
}

struct YeelightDevice {
    id: String,
    name: String,
    address: SocketAddr,
    stream: Option<TcpStream>,
    last_color: Option<[u8; 3]>,
    command_id: u64,
}

impl YeelightDevice {
    fn discover() -> Vec<Self> {
        let Ok(socket) = UdpSocket::bind("0.0.0.0:0") else {
            return Vec::new();
        };
        let _ = socket.set_read_timeout(Some(Duration::from_millis(150)));
        let Ok(multicast) = YEELIGHT_MULTICAST.parse::<SocketAddr>() else {
            return Vec::new();
        };
        if socket.send_to(YEELIGHT_DISCOVERY, multicast).is_err() {
            return Vec::new();
        }
        let deadline = Instant::now() + Duration::from_secs(3);
        let mut found = Vec::new();
        let mut ids = HashSet::new();
        let mut buffer = [0u8; 2048];
        while Instant::now() < deadline {
            let Ok((length, _source)) = socket.recv_from(&mut buffer) else {
                continue;
            };
            let response = String::from_utf8_lossy(&buffer[..length]);
            let mut id = String::new();
            let mut location = String::new();
            let mut name = String::new();
            for line in response.lines() {
                if let Some((key, value)) = line.split_once(':') {
                    match key.trim().to_ascii_lowercase().as_str() {
                        "id" => id = value.trim().to_owned(),
                        "location" => location = value.trim().to_owned(),
                        "name" => name = value.trim().to_owned(),
                        _ => {}
                    }
                }
            }
            if id.is_empty() || location.is_empty() || !ids.insert(id.clone()) {
                continue;
            }
            let Some(location) = location.strip_prefix("yeelight://") else {
                continue;
            };
            let Some((host, port)) = location.rsplit_once(':') else {
                continue;
            };
            let Ok(address) = format!("{host}:{port}").parse::<SocketAddr>() else {
                continue;
            };
            found.push(Self {
                id,
                name,
                address,
                stream: None,
                last_color: None,
                command_id: 1,
            });
        }
        found
    }

    fn connect(&mut self) -> bool {
        if self
            .stream
            .as_ref()
            .is_some_and(|stream| stream.peer_addr().is_ok())
        {
            return true;
        }
        self.disconnect();
        let Ok(stream) = TcpStream::connect_timeout(&self.address, Duration::from_secs(1)) else {
            return false;
        };
        let _ = stream.set_nodelay(true);
        let _ = stream.set_write_timeout(Some(Duration::from_secs(1)));
        let reader = match stream.try_clone() {
            Ok(reader) => reader,
            Err(_) => return false,
        };
        let _ = thread::Builder::new()
            .name(format!("MineLights-Yeelight-{}", self.id))
            .spawn(move || {
                let _ = reader.set_read_timeout(Some(Duration::from_secs(2)));
                let mut reader = BufReader::new(reader);
                let mut line = String::new();
                loop {
                    line.clear();
                    match reader.read_line(&mut line) {
                        Ok(0) => break,
                        Ok(_) => {}
                        Err(error)
                            if error.kind() == std::io::ErrorKind::WouldBlock
                                || error.kind() == std::io::ErrorKind::TimedOut => {}
                        Err(_) => break,
                    }
                }
            });
        self.stream = Some(stream);
        true
    }

    fn disconnect(&mut self) {
        if let Some(stream) = self.stream.take() {
            let _ = stream.shutdown(std::net::Shutdown::Both);
        }
    }

    fn set_color(&mut self, color: [u8; 3]) {
        if self.last_color == Some(color) {
            return;
        }
        if !self.connect() {
            return;
        }
        let rgb = (u32::from(color[0]) << 16) | (u32::from(color[1]) << 8) | u32::from(color[2]);
        let command = serde_json::json!({
            "id": self.command_id,
            "method": "set_rgb",
            "params": [rgb, "smooth", 400],
        });
        self.command_id = self.command_id.wrapping_add(1).max(1);
        let mut bytes = match serde_json::to_vec(&command) {
            Ok(bytes) => bytes,
            Err(_) => return,
        };
        bytes.extend_from_slice(b"\r\n");
        let Some(stream) = self.stream.as_mut() else {
            return;
        };
        if stream
            .write_all(&bytes)
            .and_then(|()| stream.flush())
            .is_ok()
        {
            self.last_color = Some(color);
        } else {
            self.disconnect();
        }
    }
}

impl Drop for YeelightDevice {
    fn drop(&mut self) {
        self.disconnect();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::{Ipv4Addr, TcpListener};

    #[test]
    fn openrgb_packet_uses_little_endian_header_and_payload() {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let address = listener.local_addr().unwrap();
        let reader = thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            let mut bytes = [0u8; 20];
            stream.read_exact(&mut bytes).unwrap();
            bytes
        });
        let mut client = TcpStream::connect(address).unwrap();
        send_packet(&mut client, 7, 1050, &[1, 2, 3, 4]).unwrap();
        let bytes = reader.join().unwrap();
        assert_eq!(&bytes[..4], b"ORGB");
        assert_eq!(u32::from_le_bytes(bytes[4..8].try_into().unwrap()), 7);
        assert_eq!(u32::from_le_bytes(bytes[8..12].try_into().unwrap()), 1050);
        assert_eq!(u32::from_le_bytes(bytes[12..16].try_into().unwrap()), 4);
        assert_eq!(&bytes[16..], &[1, 2, 3, 4]);
    }

    #[test]
    fn yeelight_sends_rgb_command_to_device() {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let address = listener.local_addr().unwrap();
        let reader = thread::spawn(move || {
            let (stream, _) = listener.accept().unwrap();
            let mut reader = BufReader::new(stream);
            let mut line = String::new();
            reader.read_line(&mut line).unwrap();
            serde_json::from_str::<serde_json::Value>(&line).unwrap()
        });
        let mut device = YeelightDevice {
            id: "test".into(),
            name: String::new(),
            address,
            stream: None,
            last_color: None,
            command_id: 1,
        };
        device.set_color([0x12, 0x34, 0x56]);
        let command = reader.join().unwrap();
        assert_eq!(command["method"], "set_rgb");
        assert_eq!(command["params"][0], 0x123456);
        assert_eq!(command["params"][1], "smooth");
        assert_eq!(command["params"][2], 400);
    }

    #[test]
    fn rejects_truncated_openrgb_device_data() {
        assert!(parse_openrgb_device(&[0, 1, 2], 5).is_err());
    }

    #[test]
    fn parses_openrgb_v5_device_with_led_names() {
        fn string(out: &mut Vec<u8>, value: &str) {
            out.extend_from_slice(&((value.len() + 1) as u16).to_le_bytes());
            out.extend_from_slice(value.as_bytes());
            out.push(0);
        }
        let mut packet = vec![0; 8];
        for value in ["Keyboard", "", "", "", "", ""] {
            string(&mut packet, value);
        }
        packet.extend_from_slice(&0u16.to_le_bytes());
        packet.extend_from_slice(&0u32.to_le_bytes());
        packet.extend_from_slice(&1u16.to_le_bytes());
        string(&mut packet, "Zone");
        for value in [0u32, 2, 2, 2] {
            packet.extend_from_slice(&value.to_le_bytes());
        }
        packet.extend_from_slice(&0u16.to_le_bytes());
        packet.extend_from_slice(&0u16.to_le_bytes());
        packet.extend_from_slice(&0u32.to_le_bytes());
        packet.extend_from_slice(&2u16.to_le_bytes());
        for name in ["Key: Q", "Key: W"] {
            string(&mut packet, name);
            packet.extend_from_slice(&0u32.to_le_bytes());
        }
        packet.extend_from_slice(&0u16.to_le_bytes());
        packet.extend_from_slice(&0u16.to_le_bytes());
        packet.extend_from_slice(&0u32.to_le_bytes());
        let (name, count, keys) = parse_openrgb_device(&packet, 5).unwrap();
        assert_eq!(name, "Keyboard");
        assert_eq!(count, 2);
        assert_eq!(keys, vec![(0, "Key: Q".into()), (1, "Key: W".into())]);
    }

    #[test]
    fn standardizes_corsair_led_indices_like_java() {
        assert_eq!(standardize_key("Corsair Pro LED 0"), "RAM_1");
        assert_eq!(standardize_key("Key: left control"), "LEFT_CTRL");
        assert_eq!(standardize_key("Key: Page-Down"), "PAGEDOWN");
    }
}
