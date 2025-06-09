package megabytesme.minelights.effects;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FrameStateDto {
    public Map<Integer, RGBColorDto> keys = new HashMap<>();

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FrameStateDto that = (FrameStateDto) o;
        return keys.equals(that.keys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keys);
    }
}