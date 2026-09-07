#pragma once

#include <cmath>
#include <cstdint>

class StreamFramePacer
{
public:
    enum class Result { Duplicate, WarmingUp, Interpolate, Discontinuity, DisplayTooSlow, Overloaded };

    Result source(std::uint64_t sequence, std::uint64_t timestamp,
                  std::int64_t now, double refreshRate)
    {
        if (m_hasSource && sequence == m_sequence && timestamp == m_timestamp)
            return Result::Duplicate;
        const bool consecutive = m_hasSource && sequence == m_sequence + 1
            && timestamp > m_timestamp;
        const auto interval = consecutive ? timestamp - m_timestamp : 0;
        const bool stable = interval >= 8'000'000 && interval <= 40'000'000
            && (!m_interval || (interval * 4 >= m_interval * 3
                               && interval * 4 <= m_interval * 5));
        const bool delayed = stable && now - m_arrival > 2 * std::int64_t(interval);
        const bool pending = m_pending;
        const bool first = !m_hasSource;
        m_hasSource = true;
        m_sequence = sequence;
        m_timestamp = timestamp;
        m_arrival = now;
        m_pending = false;
        m_interval = stable ? interval : 0;
        if (first) return Result::WarmingUp;
        if (!consecutive || !stable || delayed) return Result::Discontinuity;
        if (!std::isfinite(refreshRate) || refreshRate < 1.95e9 / double(interval))
            return Result::DisplayTooSlow;
        if (pending) m_cooldownUntil = now + 2'000'000'000;
        if (now < m_cooldownUntil) return Result::Overloaded;
        return Result::Interpolate;
    }

    void midpoint(std::int64_t now)
    {
        m_pending = true;
        m_originalDue = now + std::int64_t(m_interval / 2);
    }

    bool takeOriginal(std::int64_t now, double refreshRate)
    {
        if (!m_pending) return false;
        const auto lead = refreshRate > 0 ? std::int64_t(1.5e9 / refreshRate) : 0;
        if (now + lead < m_originalDue) return false;
        m_pending = false;
        return true;
    }

    bool pending() const { return m_pending; }
    void reset() { *this = {}; }

private:
    std::uint64_t m_sequence = 0;
    std::uint64_t m_timestamp = 0;
    std::uint64_t m_interval = 0;
    std::int64_t m_arrival = 0;
    std::int64_t m_originalDue = 0;
    std::int64_t m_cooldownUntil = 0;
    bool m_hasSource = false;
    bool m_pending = false;
};
