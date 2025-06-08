#pragma once
#include <string>
#include <vector>

struct Biome {
    std::string name;
    bool hasRain;
    bool isSnowy;
    int red;
    int green;
    int blue;
};

extern std::vector<Biome> biomes;