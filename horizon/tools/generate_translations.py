from pathlib import Path
import json
import subprocess

root = Path(__file__).resolve().parents[2]
tracked = subprocess.check_output(
    ['git', 'ls-files', 'common/src/main/resources/assets/minelights/lang/*.json'],
    cwd=root, text=True,
).splitlines()
keys = {
    0: 'option.mine-lights.enableMod', 1: 'option.mine-lights.enableExperienceBar',
    2: 'option.mine-lights.enableHealthBar', 3: 'option.mine-lights.enableHungerBar',
    4: 'option.mine-lights.enableSaturationBar', 5: 'option.mine-lights.enableCompassEffect',
    6: 'option.mine-lights.enableBiomeEffects', 7: 'option.mine-lights.dimmingMode',
    9: 'option.mine-lights.enableWeatherEffects', 10: 'option.mine-lights.mirrorRamRainDirection',
    11: 'option.mine-lights.enableEndFlashEffect', 12: 'option.mine-lights.enableOnFireEffect',
    13: 'option.mine-lights.enableInWaterEffect', 14: 'option.mine-lights.enablePortalEffects',
    15: 'option.mine-lights.enableLowHealthWarning', 16: 'option.mine-lights.highlightMovementKeys',
    17: 'option.mine-lights.pulseChatKey', 18: 'option.mine-lights.alwaysShowCompass',
    19: 'option.mine-lights.enableLocatorBar', 20: 'option.mine-lights.autoStartServer',
    21: 'option.mine-lights.compassPriority', 22: 'integration.mine-lights.corsair',
    23: 'integration.mine-lights.asus', 24: 'integration.mine-lights.logitech',
    25: 'integration.mine-lights.razer', 26: 'integration.mine-lights.wooting',
    27: 'integration.mine-lights.steelseries', 28: 'integration.mine-lights.msi',
    29: 'integration.mine-lights.novation', 30: 'integration.mine-lights.picopi',
    31: 'integration.mine-lights.openrgb', 32: 'integration.mine-lights.yeelight',
    33: 'option.mine-lights.refresh_devices.label', 34: 'option.mine-lights.restart.label',
    35: 'option.mine-lights.restart_admin.label', 36: 'option.mine-lights.force_update.label',
    37: 'option.mine-lights.clear_disabled.label',
    40: 'enum.mine-lights.dimmingMode.local_light', 41: 'enum.mine-lights.dimmingMode.sky_light',
    42: 'enum.mine-lights.dimmingMode.none', 43: 'enum.mine-lights.compassPriority.priority',
    44: 'enum.mine-lights.compassPriority.standard_first', 45: 'enum.mine-lights.compassPriority.lodestone_first',
    46: 'enum.mine-lights.compassPriority.recovery_first',
}
lines = ['// Setting names copied from MineLights language resources.',
         'pub fn setting_label(language: &str, index: usize) -> Option<&\'static str> {',
         '    match language {']
count = 0
for path in sorted(root / item for item in tracked):
    data = json.loads(path.read_text(encoding='utf-8-sig'))
    values = [(index, data[key]) for index, key in keys.items() if isinstance(data.get(key), str)]
    if not values:
        continue
    lines.append(f'        {json.dumps(path.stem)} => match index {{')
    for index, value in values:
        lines.append(f'            {index} => Some({json.dumps(value, ensure_ascii=False)}),')
    lines.extend(['            _ => None,', '        },'])
    count += 1
lines.extend(['        _ => None,', '    }', '}', ''])
(root / 'horizon/mixins/minelights/translations.rs').write_text('\n'.join(lines), encoding='utf-8', newline='\n')
print(f'Generated settings translations for {count} languages.')
