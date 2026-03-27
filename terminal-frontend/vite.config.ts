import { defineConfig, type Plugin } from "vite";

/** Strip crossorigin attributes that break local WebViewAssetLoader URLs. */
function stripCrossorigin(): Plugin {
    return {
        name: "strip-crossorigin",
        enforce: "post",
        transformIndexHtml(html) {
            return html.replace(/ crossorigin/g, "");
        },
    };
}

export default defineConfig({
    base: "./",
    plugins: [stripCrossorigin()],
    test: {
        environment: "jsdom",
    },
    build: {
        outDir: "../app/src/main/assets/www",
        emptyOutDir: true,
        assetsInlineLimit: 0,
        target: "es2020",
        cssTarget: "chrome61",
        modulePreload: false,
        rollupOptions: {
            output: {
                format: "es",
                entryFileNames: "assets/[name].js",
                chunkFileNames: "assets/[name].js",
                assetFileNames: "assets/[name].[ext]",
            },
        },
    },
});
