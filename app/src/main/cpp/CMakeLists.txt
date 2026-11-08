cmake_minimum_required(VERSION 3.22.1)
project(flame_jvm_bridge LANGUAGES CXX)

add_library(
    flame_jvm_bridge
    SHARED
    native_jvm_library_probe.cpp
)

target_compile_features(flame_jvm_bridge PRIVATE cxx_std_17)
target_compile_options(flame_jvm_bridge PRIVATE -Wall -Wextra -Werror)
target_link_libraries(flame_jvm_bridge PRIVATE dl log)
