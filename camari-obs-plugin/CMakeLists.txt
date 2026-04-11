cmake_minimum_required(VERSION 3.16)
project(camari-obs-plugin VERSION 0.1.0 LANGUAGES C)

set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)

include(FetchContent)

# ── OBS Studio SDK ────────────────────────────────────────────────────────────
# Download OBS source for headers only; do not build OBS itself.
# On macOS, -undefined dynamic_lookup lets OBS symbols resolve at load time.
# On Windows, obs.lib must be present (installed via OBS Studio).
FetchContent_Declare(
  obs-studio
  GIT_REPOSITORY https://github.com/obsproject/obs-studio.git
  GIT_TAG        31.1.1
  GIT_SHALLOW    TRUE
  GIT_SUBMODULES ""
)
FetchContent_GetProperties(obs-studio)
if(NOT obs-studio_POPULATED)
  FetchContent_Populate(obs-studio)
endif()

add_library(OBS_headers INTERFACE)
target_include_directories(OBS_headers INTERFACE
  "${obs-studio_SOURCE_DIR}/libobs"
  "${obs-studio_SOURCE_DIR}"
)

# ── Plugin library ────────────────────────────────────────────────────────────
add_library(camari-obs-plugin MODULE)
set_target_properties(camari-obs-plugin PROPERTIES
  PREFIX ""
  OUTPUT_NAME "camari-obs-plugin"
)

target_link_libraries(camari-obs-plugin PRIVATE OBS_headers)

if(APPLE)
  # OBS symbols live in the host process; resolve them at load time
  target_link_options(camari-obs-plugin PRIVATE -undefined dynamic_lookup)
elseif(UNIX)
  # Linux: same idea — OBS symbols come from the host process at load time
  target_link_options(camari-obs-plugin PRIVATE -Wl,--allow-shlib-undefined)
elseif(WIN32)
  # obs.lib is installed alongside OBS Studio on Windows
  find_library(OBS_LIB
    NAMES obs
    HINTS
      "C:/Program Files/obs-studio/bin/64bit"
      "$ENV{ProgramFiles}/obs-studio/bin/64bit"
  )
  if(OBS_LIB)
    target_link_libraries(camari-obs-plugin PRIVATE "${OBS_LIB}")
  else()
    message(FATAL_ERROR "obs.lib not found. Install OBS Studio or set OBS_LIB.")
  endif()
endif()

# ── libcurl ───────────────────────────────────────────────────────────────────
find_package(CURL QUIET)
if(NOT CURL_FOUND)
  FetchContent_Declare(
    CURL
    URL      https://github.com/curl/curl/releases/download/curl-8_8_0/curl-8.8.0.tar.gz
    URL_HASH SHA256=2a98189b99bd9c8e704efd44503a5ce682e6a5b2c9b663c0b36c1e533bce23c3
  )
  FetchContent_MakeAvailable(CURL)
endif()
target_link_libraries(camari-obs-plugin PRIVATE CURL::libcurl)

# ── libjpeg-turbo ─────────────────────────────────────────────────────────────
set(ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(ENABLE_STATIC ON  CACHE BOOL "" FORCE)
FetchContent_Declare(
  libjpeg-turbo
  GIT_REPOSITORY https://github.com/libjpeg-turbo/libjpeg-turbo.git
  GIT_TAG        3.0.3
)
FetchContent_MakeAvailable(libjpeg-turbo)

if(TARGET turbojpeg-static)
  target_link_libraries(camari-obs-plugin PRIVATE turbojpeg-static)
  target_include_directories(camari-obs-plugin PRIVATE
    "${libjpeg-turbo_SOURCE_DIR}"
    "${libjpeg-turbo_BINARY_DIR}"
  )
else()
  find_package(libjpeg-turbo REQUIRED)
  target_link_libraries(camari-obs-plugin PRIVATE libjpeg-turbo::turbojpeg-static)
endif()

# ── mjansson/mdns ─────────────────────────────────────────────────────────────
FetchContent_Declare(
  mdns
  GIT_REPOSITORY https://github.com/mjansson/mdns.git
  GIT_TAG        1.4.3
)
FetchContent_MakeAvailable(mdns)
target_include_directories(camari-obs-plugin PRIVATE "${mdns_SOURCE_DIR}")

if(WIN32)
  target_link_libraries(camari-obs-plugin PRIVATE ws2_32 iphlpapi)
endif()

# ── Sources ───────────────────────────────────────────────────────────────────
target_sources(camari-obs-plugin PRIVATE
  src/plugin-main.c
  src/camari-source.c
  src/mjpeg-parser.c
  src/reconnect.c
  src/mdns-discovery.c
)

# ── Tests ─────────────────────────────────────────────────────────────────────
if(BUILD_TESTING)
  enable_testing()

  add_executable(test-mjpeg-parser tests/test-mjpeg-parser.c src/mjpeg-parser.c)
  target_include_directories(test-mjpeg-parser PRIVATE src "${obs-studio_SOURCE_DIR}/libobs")
  add_test(NAME mjpeg-parser COMMAND test-mjpeg-parser)

  add_executable(test-reconnect tests/test-reconnect.c src/reconnect.c)
  target_include_directories(test-reconnect PRIVATE src)
  add_test(NAME reconnect COMMAND test-reconnect)
endif()
