#include <iostream>
#include <fstream>
#include <string>
#include <chrono>
#include <thread>

namespace {
std::string field(const std::string &json, const std::string &name)
{
    const auto marker = std::string{"\""} + name + "\":\"";
    const auto start = json.find(marker);
    if (start == std::string::npos) return {};
    const auto valueStart = start + marker.size();
    const auto end = json.find('"', valueStart);
    return end == std::string::npos ? std::string{} : json.substr(valueStart, end - valueStart);
}
}

int main(int argc, char **argv)
{
    std::string eofMarker;
    if (argc == 3 && std::string(argv[1]) == "--eof-marker") {
        eofMarker = argv[2];
    }
    std::string line;
    while (std::getline(std::cin, line)) {
        const auto id = field(line, "id");
        const auto method = field(line, "method");
        if (method == "core.hello") {
            std::cout << "{\"type\":\"response\",\"id\":\"" << id
                      << "\",\"ok\":true,\"result\":{\"protocolVersion\":1}}\n" << std::flush;
        } else if (method == "test.echo") {
            std::cout << "{\"type\":\"response\",\"id\":\"" << id
                      << "\",\"ok\":true,\"result\":{\"value\":\"pong\"}}\n" << std::flush;
        } else if (method == "test.event") {
            std::cout << "{\"type\":\"event\",\"name\":\"catalog.changed\",\"payload\":{\"revision\":2}}\n";
            std::cout << "{\"type\":\"response\",\"id\":\"" << id
                      << "\",\"ok\":true,\"result\":{}}\n" << std::flush;
        } else if (method == "test.error") {
            std::cout << "{\"type\":\"response\",\"id\":\"" << id
                      << "\",\"ok\":false,\"error\":{\"code\":\"expected\",\"message\":\"Expected failure\"}}\n" << std::flush;
        } else if (method == "test.hang") {
            continue;
        } else if (method == "test.partial") {
            std::cout << "{\"type\":\"response\",\"id\":\"" << id << std::flush;
            std::this_thread::sleep_for(std::chrono::milliseconds(25));
            std::cout << "\",\"ok\":true,\"result\":{\"fragmented\":true}}\n" << std::flush;
        } else if (method == "test.exit") {
            return 23;
        }
    }
    if (!eofMarker.empty()) {
        std::ofstream marker(eofMarker, std::ios::binary | std::ios::trunc);
        marker << "graceful";
        marker.flush();
    }
    return 0;
}
