#include "Logger.h"
#include <chrono>
#include <iomanip>
#include <sstream>

Logger& Logger::GetInstance() {
    static Logger instance;
    return instance;
}

Logger::Logger() {
    m_logFile.open("MineLights_Server.log", std::ios::out | std::ios::app);
}

Logger::~Logger() {
    if (m_logFile.is_open()) {
        m_logFile.close();
    }
}

void Logger::Log(const std::string& message) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_logFile.is_open()) {
        auto now = std::chrono::system_clock::now();
        auto time_t_now = std::chrono::system_clock::to_time_t(now);
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()) % 1000;

        tm timeinfo;
        localtime_s(&timeinfo, &time_t_now);

        std::stringstream ss;
        ss << std::put_time(&timeinfo, "[%Y-%m-%d %H:%M:%S");
        ss << '.' << std::setfill('0') << std::setw(3) << ms.count() << "] ";

        m_logFile << ss.str() << message << std::endl;
    }
}