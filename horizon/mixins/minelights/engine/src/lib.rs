use std::collections::HashMap;

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct DeviceLayout {
    pub keys: HashMap<String, Vec<u32>>,
    pub leds: Vec<u32>,
    pub unique_id: String,
    pub legacy_id: Option<String>,
    pub display_name: String,
    pub integration: String,
}

pub fn standardize_key(key: &str) -> String {
    let mut name = key
        .trim()
        .to_ascii_uppercase()
        .replace("CONTROL", "CTRL")
        .replace("WINDOWS", "WIN");
    if let Some(stripped) = name.strip_prefix("KEY: ") {
        name = stripped.to_owned();
    }
    if name.contains("PROGRAMMABLE") {
        return format!("G{}", trailing_number(&name));
    }
    if name.contains("DRAM") || name.contains("CORSAIR PRO LED") || name.contains("RAM") {
        let number = trailing_number(&name);
        if name.contains("CORSAIR PRO LED") {
            if let Ok(index) = number.parse::<u32>() {
                return format!("RAM_{}", index.saturating_add(1));
            }
        }
        return if number.is_empty() {
            "RAM".into()
        } else {
            format!("RAM_{number}")
        };
    }
    if name.contains("LEDSTRIPE") || name.contains("UNDERGLOW") {
        let number = trailing_number(&name);
        return if number.is_empty() {
            "UNDERGLOW".into()
        } else {
            format!("UNDERGLOW_{number}")
        };
    }
    if name.contains("CUSTOM") || name.contains("WHEEL") {
        let number = trailing_number(&name);
        return if number.is_empty() {
            "WHEEL".into()
        } else {
            format!("WHEEL_{number}")
        };
    }
    if name.contains("PLAYER") {
        let number = trailing_number(&name);
        return if number.is_empty() {
            "PLAYER".into()
        } else {
            format!("PLAYER_{number}")
        };
    }
    name.split_whitespace()
        .collect::<Vec<_>>()
        .join("_")
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || *c == '_')
        .collect()
}

fn trailing_number(value: &str) -> String {
    value
        .chars()
        .rev()
        .take_while(|c| c.is_ascii_digit())
        .collect::<String>()
        .chars()
        .rev()
        .collect()
}

mod protocol;

pub use protocol::*;
