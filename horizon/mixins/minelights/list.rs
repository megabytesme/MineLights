// Show MineLights in Options > Mods.
fn minelights_list(mods: &mut Vec<crate::game::native_hooks::NativeMod>) {
    mods.push(crate::game::native_hooks::NativeMod {
        id: "minelights".into(),
        title: "MineLights 0.1.0".into(),
        description: "RGB lighting; uses local device connections and can download MineLights Server from GitHub".into(),
        keys: Vec::new(),
    });
}
