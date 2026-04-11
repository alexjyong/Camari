cmake_minimum_required(VERSION 3.28...3.30)

include("${CMAKE_CURRENT_SOURCE_DIR}/cmake/common/bootstrap.cmake" NO_POLICY_SCOPE)

project(${_name} VERSION ${_version})

option(ENABLE_FRONTEND_API "Use obs-frontend-api for UI functionality" OFF)
option(ENABLE_QT "Use Qt functionality" OFF)

include(compilerconfig)
include(defaults)
include(helpers)

# ── Plugin library ────────────────────────────────────────────────────────────
add_library(${CMAKE_PROJECT_NAME} MODULE)

find_package(libobs REQUIRED)
target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE OBS::libobs)

# ── libcurl ───────────────────────────────────────────────────────────────────
include(FetchContent)

FetchContent_Declare(
  CURL
  URL https://github.com/curl/curl/releases/download/curl-8_8_0/curl-8.8.0.tar.gz
  URL_HASH SHA256=2a98189b99bd9c8e704efd44503a5ce682e6a5b2c9b663c0b36c1e533bce23c3
  FIND_PACKAGE_ARGS NAMES CURL
)
FetchContent_MakeAvailable(CURL)

find_package(CURL REQUIRED)
target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE CURL::libcurl)

# ── libjpeg-turbo ─────────────────────────────────────────────────────────────
FetchContent_Declare(
  libjpeg-turbo
  GIT_REPOSITORY https://github.com/libjpeg-turbo/libjpeg-turbo.git
  GIT_TAG 3.0.3
  FIND_PACKAGE_ARGS NAMES libjpeg-turbo
)
set(ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(ENABLE_STATIC ON  CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(libjpeg-turbo)

if(TARGET turbojpeg-static)
  target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE turbojpeg-static)
  target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    "${libjpeg-turbo_SOURCE_DIR}"
    "${libjpeg-turbo_BINARY_DIR}"
  )
else()
  find_package(libjpeg-turbo REQUIRED)
  target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE libjpeg-turbo::turbojpeg-static)
endif()

# ── mjansson/mdns (single-header mDNS library) ────────────────────────────────
FetchContent_Declare(
  mdns
  GIT_REPOSITORY https://github.com/mjansson/mdns.git
  GIT_TAG        1.4.3
)
FetchContent_MakeAvailable(mdns)
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE "${mdns_SOURCE_DIR}")

if(WIN32)
  target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE ws2_32 iphlpapi)
endif()

# ── Sources ───────────────────────────────────────────────────────────────────
target_sources(
  ${CMAKE_PROJECT_NAME}
  PRIVATE
    src/plugin-main.c
    src/camari-source.c
    src/mjpeg-parser.c
    src/reconnect.c
    src/mdns-discovery.c
)

target_compile_features(${CMAKE_PROJECT_NAME} PRIVATE c_std_17)

set_target_properties_plugin(${CMAKE_PROJECT_NAME} PROPERTIES OUTPUT_NAME ${_name})

# ── Tests ─────────────────────────────────────────────────────────────────────
if(BUILD_TESTING)
  enable_testing()

  add_executable(test-mjpeg-parser tests/test-mjpeg-parser.c src/mjpeg-parser.c)
  target_include_directories(test-mjpeg-parser PRIVATE src)
  add_test(NAME mjpeg-parser COMMAND test-mjpeg-parser)

  add_executable(test-reconnect tests/test-reconnect.c src/reconnect.c)
  target_include_directories(test-reconnect PRIVATE src)
  add_test(NAME reconnect COMMAND test-reconnect)
endif()
