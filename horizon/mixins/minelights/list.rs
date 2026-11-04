// Show MineLights in Options > Mods.
fn minelights_list(mods: &mut Vec<crate::game::native_hooks::NativeMod>) {
    mods.push(crate::game::native_hooks::NativeMod {
        id: "minelights".into(),
        title: "MineLights 2.3.8.5".into(),
        description: "RGB lighting; local device connections, optional update checks and opt-in verified server download".into(),
        keys: Vec::new(),
    });
}
