$ndk_bin="C:\Users\airi\AppData\Local\Android\Sdk\ndk\30.0.16138531\toolchains\llvm\prebuilt\windows-x86_64\bin"
$env:CC_x86_64_linux_android="$ndk_bin\x86_64-linux-android35-clang.cmd"
$env:AR_x86_64_linux_android="$ndk_bin\llvm-ar.exe"
$env:CC_aarch64_linux_android="$ndk_bin\aarch64-linux-android35-clang.cmd"
$env:AR_aarch64_linux_android="$ndk_bin\llvm-ar.exe"

cd rust-sdk
cargo build --target x86_64-linux-android --lib --features android
cargo build --target aarch64-linux-android --lib --features android
cargo run --features uniffi/cli --bin uniffi-bindgen generate src/sonark.udl --language kotlin --out-dir ../app/src/main/java
cd ..

cp rust-sdk/target/x86_64-linux-android/debug/libuniffi_sonark_sdk.so app/src/main/jniLibs/x86_64/libuniffi_sonark_sdk.so
cp rust-sdk/target/aarch64-linux-android/debug/libuniffi_sonark_sdk.so app/src/main/jniLibs/arm64-v8a/libuniffi_sonark_sdk.so
