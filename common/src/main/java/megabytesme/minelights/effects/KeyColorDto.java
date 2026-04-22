package megabytesme.minelights.effects;

public class KeyColorDto {
    public int id;
    public int r, g, b;

    public KeyColorDto() {
    }

    public KeyColorDto(Integer id, int r, int g, int b) {
        this.id = id;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public KeyColorDto(Integer id, RGBColorDto color) {
        this.id = id;
        this.r = color.r;
        this.g = color.g;
        this.b = color.b;
    }
}