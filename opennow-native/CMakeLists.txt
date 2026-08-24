cmake_minimum_required(VERSION 3.21)

project(OpenNOWNative VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt6 6.4 REQUIRED COMPONENTS Core Gui Qml Quick)

include(FetchContent)
set(SDL_SHARED OFF CACHE BOOL "" FORCE)
set(SDL_STATIC ON CACHE BOOL "" FORCE)
set(SDL_TEST_LIBRARY OFF CACHE BOOL "" FORCE)
set(SDL_TESTS OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    SDL3
    GIT_REPOSITORY https://github.com/libsdl-org/SDL.git
    GIT_TAG release-3.4.12
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(SDL3)

qt_standard_project_setup()

qt_add_executable(opennow-native
    src/main.cpp
    src/controllerinput.cpp
    src/controllerinput.h
    src/streamengine.cpp
    src/streamengine.h
)

set_source_files_properties(qml/Theme.qml PROPERTIES QT_QML_SINGLETON_TYPE TRUE)

qt_add_qml_module(opennow-native
    URI OpenNOW
    VERSION 1.0
    QML_FILES
        qml/Main.qml
        qml/Theme.qml
        qml/components/ActionButton.qml
        qml/components/FocusCard.qml
        qml/components/GameArtwork.qml
        qml/components/GameCard.qml
        qml/components/NavigationRail.qml
        qml/components/SettingRow.qml
        qml/pages/HomePage.qml
        qml/pages/LibraryPage.qml
        qml/pages/SearchPage.qml
        qml/pages/SettingsPage.qml
        qml/pages/GameDetailPage.qml
        qml/pages/StreamPage.qml
    RESOURCES
        assets/opennow-mark.png
)

target_link_libraries(opennow-native PRIVATE
    Qt6::Core
    Qt6::Gui
    Qt6::Qml
    Qt6::Quick
    SDL3::SDL3-static
)

install(TARGETS opennow-native
    BUNDLE DESTINATION .
    RUNTIME DESTINATION bin
)
