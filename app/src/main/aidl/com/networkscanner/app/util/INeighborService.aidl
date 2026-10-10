package com.networkscanner.app.util;

interface INeighborService {
    void destroy() = 16777114; // Reserved by the Shizuku server

    List<String> neighbors() = 1;
}
