// Configures webpack-dev-server to send the headers required for SharedArrayBuffer.
if (config.devServer) {
    config.devServer.headers = {
        ...config.devServer.headers,
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Resource-Policy": "same-site",
        "Cross-Origin-Embedder-Policy": "require-corp",
    };
}
