cmake_minimum_required(VERSION 3.22.1)

project("terminalide")

add_library(
    terminalide
    SHARED
    native-lib.cpp
)

find_library(log-lib log)

target_link_libraries(
    terminalide
    ${log-lib}
)
