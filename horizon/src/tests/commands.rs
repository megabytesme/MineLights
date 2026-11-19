use crate::minelights_commands;
use std::collections::HashSet;

#[test]
fn client_registration_keeps_aliases_and_multiword_arguments() {
    let mut commands = Vec::new();
    minelights_commands::register(&mut commands);
    let usages: HashSet<&str> = commands
        .iter()
        .map(|command| command.usage.as_str())
        .collect();
    assert_eq!(usages.len(), commands.len());
    assert!(commands.iter().all(|command| command.level == 0));
    for required in [
        "minelights",
        "minelights help",
        "minelights list",
        "minelights settings",
        "minelights get <setting:word>",
        "minelights set <setting:word> <value:text>",
        "minelights toggle <setting:word>",
        "minelights confirm server-autostart",
        "minelights devices clear",
        "minelights clear-disabled",
        "minelights clear-disabled-devices",
        "minelights server import <path:text>",
        "minelights server remove <server-id:word>",
        "minelights server forget <server-id:word>",
        "minelights server stop [server-id:word]",
    ] {
        assert!(usages.contains(required), "missing /{required}");
    }
    for alias in [
        "restart",
        "restart-admin",
        "restart-as-admin",
        "check-update",
        "check-updates",
    ] {
        assert!(usages.contains(format!("minelights {alias}").as_str()));
        assert!(usages.contains(format!("minelights server {alias}").as_str()));
    }
}
