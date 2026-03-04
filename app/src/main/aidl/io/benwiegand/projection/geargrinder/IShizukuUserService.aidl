package io.benwiegand.projection.geargrinder;

interface IShizukuUserService {
    void execPrivd(String scriptPath, in Map<String, String> env) = 1;
    void killPrivd() = 2;

    void destroy() = 16777114;
}