#pragma once
#include <string>

struct Player {
    bool inGame;
    float health;
    float hunger;
    float experience;
    std::string weather;
    std::string currentBlock;
    std::string currentBiome;
    bool isOnFire;
    bool isPoisoned;
    bool isWithering;
    bool isTakingDamage;
};

extern Player player;