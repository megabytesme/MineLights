package megabytesme.minelights.effects;

import java.util.Objects;

public class RGBColorDto {
    public int r, g, b;

    public RGBColorDto() {
        this.r = 0;
        this.g = 0;
        this.b = 0;
    }

    public RGBColorDto(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RGBColorDto that = (RGBColorDto) o;
        return r == that.r && g == that.g && b == that.b;
    }

    @Override
    public int hashCode() {
        return Objects.hash(r, g, b);
    }
}