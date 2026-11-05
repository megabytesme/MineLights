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
    let symbol = match name.as_str() {
        "`" => Some("BACKTICK"),
        "-" => Some("MINUS"),
        "=" => Some("EQUALS"),
        "[" => Some("LEFT_BRACKET"),
        "]" => Some("RIGHT_BRACKET"),
        "\\" | "\\ (ANSI)" => Some("BACKSLASH"),
        "\\ (ISO)" => Some("ISO_BACKSLASH"),
        "#" | "# (ISO)" => Some("HASH"),
        ";" => Some("SEMICOLON"),
        "'" => Some("APOSTROPHE"),
        "," => Some("COMMA"),
        "." => Some("PERIOD"),
        "/" => Some("SLASH"),
        _ => None,
    };
    if let Some(symbol) = symbol {
        return symbol.into();
    }
    if let Some(key) = name.strip_prefix("NUMBER PAD ") {
        return match key.trim() {
            "/" => "NUMPAD_DIVIDE".into(),
            "*" => "NUMPAD_MULTIPLY".into(),
            "-" => "NUMPAD_SUBTRACT".into(),
            "+" => "NUMPAD_ADD".into(),
            "." => "NUMPAD_DECIMAL".into(),
            "ENTER" => "NUMPAD_ENTER".into(),
            other => format!(
                "NUMPAD{}",
                other
                    .chars()
                    .filter(|c| c.is_ascii_alphanumeric())
                    .collect::<String>()
            ),
        };
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
    let normalized: String = name
        .split_whitespace()
        .collect::<Vec<_>>()
        .join("_")
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || *c == '_')
        .collect();
    match normalized.as_str() {
        "LEFT_CTRL" => "LCTRL",
        "RIGHT_CTRL" => "RCTRL",
        "LEFT_SHIFT" => "LSHIFT",
        "RIGHT_SHIFT" => "RSHIFT",
        "LEFT_ALT" => "LALT",
        "RIGHT_ALT" => "RALT",
        "LEFT_WIN" | "LEFT_SUPER" => "LWIN",
        "RIGHT_WIN" => "RWIN",
        "LEFT_FN" | "RIGHT_FN" => "FN",
        "CAPS_LOCK" => "CAPSLOCK",
        "NUM_LOCK" => "NUMLOCK",
        "SCROLL_LOCK" => "SCROLLLOCK",
        "PRINT_SCREEN" => "PRINTSCREEN",
        "UP_ARROW" => "UP",
        "DOWN_ARROW" => "DOWN",
        "LEFT_ARROW" => "LEFT",
        "RIGHT_ARROW" => "RIGHT",
        "GRAVE" | "GRAVE_ACCENT" => "BACKTICK",
        "ESC" => "ESCAPE",
        _ => &normalized,
    }
    .into()
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
