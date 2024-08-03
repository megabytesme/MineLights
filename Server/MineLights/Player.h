#pragma once
#include <string>

struct Player {
    bool inGame;
    float health;
    float hunger;
    std::string weather;
    std::string currentBlock;
    std::string currentBiome;
};

extern Player player;