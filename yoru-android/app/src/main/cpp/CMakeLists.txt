cmake_minimum_required(VERSION 3.22.1)
project("native-media")

add_library(
    native-media
    SHARED
    native_media.c
)

find_library(
    log-lib
    log
)

target_link_libraries(
    native-media
    ${log-lib}
)
