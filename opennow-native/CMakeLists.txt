cmake_minimum_required(VERSION 3.21)

project(OpenNOWNative VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt6 6.4 REQUIRED COMPONENTS Core Gui Network Qml Quick)

# Paper's OpenNOW design uses these three families explicitly. Download pinned,
# hash-verified variable fonts into the build tree so packaged builds render the
# same typography even when the host does not have the fonts installed.
set(OPENNOW_FONT_DIR "${CMAKE_CURRENT_BINARY_DIR}/fonts")
file(MAKE_DIRECTORY "${OPENNOW_FONT_DIR}")

function(opennow_download_font file_name url sha256)
    set(destination "${OPENNOW_FONT_DIR}/${file_name}")
    if(NOT EXISTS "${destination}")
        file(DOWNLOAD "${url}" "${destination}"
            EXPECTED_HASH "SHA256=${sha256}"
            TLS_VERIFY ON
            STATUS download_status)
        list(GET download_status 0 download_code)
        list(GET download_status 1 download_message)
        if(NOT download_code EQUAL 0)
            file(REMOVE "${destination}")
            message(FATAL_ERROR "Failed to download ${file_name}: ${download_message}")
        endif()
    endif()
    set_source_files_properties("${destination}" PROPERTIES
        QT_RESOURCE_ALIAS "fonts/${file_name}")
    set(OPENNOW_FONT_FILES ${OPENNOW_FONT_FILES} "${destination}" PARENT_SCOPE)
endfunction()

opennow_download_font(
    "SpaceGrotesk-Variable.ttf"
    "https://raw.githubusercontent.com/google/fonts/main/ofl/spacegrotesk/SpaceGrotesk%5Bwght%5D.ttf"
    "acad6de1fc93436f5c0f1f4137751ef04f1aea3063e7036535970ffcfbd79f72")
opennow_download_font(
    "Inter-Variable.ttf"
    "https://raw.githubusercontent.com/google/fonts/main/ofl/inter/Inter%5Bopsz,wght%5D.ttf"
    "29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031")
opennow_download_font(
    "JetBrainsMono-Variable.ttf"
    "https://raw.githubusercontent.com/google/fonts/main/ofl/jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf"
    "48715a42ec242c21e9f02692891e147d022299a52e48d5e413e1a942193ffeda")

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
    src/appstate.cpp
    src/appstate.h
    src/controllerinput.cpp
    src/controllerinput.h
    src/authengine.cpp
    src/authengine.h
    src/auth/authdata.h
    src/auth/sessionstore.h
    src/gfn/catalogengine.cpp
    src/gfn/catalogengine.h
    src/gfn/cloudmatchprotocol.cpp
    src/gfn/cloudmatchprotocol.h
    src/gfn/sessionengine.cpp
    src/gfn/sessionengine.h
    src/gfn/signalingclient.cpp
    src/gfn/signalingclient.h
    src/streamengine.cpp
    src/streamengine.h
)

set_source_files_properties(qml/Theme.qml PROPERTIES QT_QML_SINGLETON_TYPE TRUE)
set_source_files_properties(qml/StreamFormat.qml PROPERTIES QT_QML_SINGLETON_TYPE TRUE)

qt_add_qml_module(opennow-native
    URI OpenNOW
    VERSION 1.0
    QML_FILES
        qml/Main.qml
        qml/Theme.qml
        qml/StreamFormat.qml
        qml/components/ActionButton.qml
        qml/components/BrandMark.qml
        qml/components/BatteryIndicator.qml
        qml/components/ControllerStatusBanner.qml
        qml/components/DitherAvatar.qml
        qml/components/FocusCard.qml
        qml/components/GameArtwork.qml
        qml/components/GameCard.qml
        qml/components/KeyHint.qml
        qml/components/NavigationRail.qml
        qml/components/SettingRow.qml
        qml/components/StoreBrandIcon.qml
        qml/components/ToastBanner.qml
        qml/components/ServerSelectorDialog.qml
        qml/pages/SignInPage.qml
        qml/pages/HomePage.qml
        qml/pages/LibraryPage.qml
        qml/pages/SearchPage.qml
        qml/pages/SessionsPage.qml
        qml/pages/SettingsPage.qml
        qml/pages/GameDetailPage.qml
        qml/pages/StreamPage.qml
        qml/pages/ProfilePickerPage.qml
    RESOURCES
        assets/opennow-mark.png
        assets/store-icons/steam.svg
        assets/store-icons/epic.svg
        assets/store-icons/ubisoft.svg
        assets/store-icons/battlenet.svg
        ${OPENNOW_FONT_FILES}
)

target_link_libraries(opennow-native PRIVATE
    Qt6::Core
    Qt6::Gui
    Qt6::Network
    Qt6::Qml
    Qt6::Quick
    SDL3::SDL3-static
)

install(TARGETS opennow-native
    BUNDLE DESTINATION .
    RUNTIME DESTINATION bin
)

include(CTest)
if(BUILD_TESTING)
    find_package(Qt6 6.4 REQUIRED COMPONENTS Test)

    qt_add_executable(catalogengine-parsertest
        src/gfn/tests/catalogengine_parsertest.cpp
        src/gfn/catalogengine.cpp
        src/gfn/catalogengine.h
    )
    target_link_libraries(catalogengine-parsertest PRIVATE Qt6::Core Qt6::Network Qt6::Test)
    add_test(NAME catalogengine-parsertest COMMAND catalogengine-parsertest)

    qt_add_executable(authdata-test
        src/auth/tests/authdata_test.cpp
        src/auth/authdata.h
        src/auth/sessionstore.h
    )
    target_link_libraries(authdata-test PRIVATE Qt6::Core Qt6::Test)
    add_test(NAME authdata-test COMMAND authdata-test)

    qt_add_executable(cloudmatchprotocol-test
        src/gfn/tests/cloudmatchprotocol_test.cpp
        src/gfn/cloudmatchprotocol.cpp
        src/gfn/cloudmatchprotocol.h
    )
    target_link_libraries(cloudmatchprotocol-test PRIVATE Qt6::Core Qt6::Network Qt6::Test)
    add_test(NAME cloudmatchprotocol-test COMMAND cloudmatchprotocol-test)

    qt_add_executable(signalingclient-test
        src/gfn/tests/signalingclient_test.cpp
        src/gfn/signalingclient.cpp
        src/gfn/signalingclient.h
    )
    target_link_libraries(signalingclient-test PRIVATE Qt6::Core Qt6::Network Qt6::Test)
    add_test(NAME signalingclient-test COMMAND signalingclient-test)
endif()
