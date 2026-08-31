use std::process::Command;
use std::fs;
use std::path::Path;

#[test]
#[ignore] // Requires network and valid token.txt
fn test_cli_sync() {
    let project_root = Path::new(env!("CARGO_MANIFEST_DIR")).parent().unwrap();
    let db_path = project_root.join("test_sonark.db");

    // Ensure clean state
    if db_path.exists() {
        fs::remove_file(&db_path).unwrap();
    }

    let output = Command::new("cargo")
        .arg("run")
        .arg("--bin")
        .arg("sonark-cli")
        .arg("--")
        .arg("--db")
        .arg(db_path.to_str().unwrap())
        .arg("sync")
        .current_dir(project_root.join("rust-sdk"))
        .output()
        .expect("Failed to execute CLI");

    println!("CLI Output: {}", String::from_utf8_lossy(&output.stdout));
    println!("CLI Error: {}", String::from_utf8_lossy(&output.stderr));

    assert!(output.status.success());
    assert!(db_path.exists());

    // Check if some songs were found
    assert!(String::from_utf8_lossy(&output.stdout).contains("Sync complete! Found"));

    // Cleanup
    fs::remove_file(&db_path).unwrap();
}

#[test]
#[ignore]
fn test_cli_stats() {
    let project_root = Path::new(env!("CARGO_MANIFEST_DIR")).parent().unwrap();
    let db_path = project_root.join("test_stats.db");

    // Create an empty DB
    let _ = Command::new("cargo")
        .arg("run")
        .arg("--bin")
        .arg("sonark-cli")
        .arg("--")
        .arg("--db")
        .arg(db_path.to_str().unwrap())
        .arg("stats")
        .current_dir(project_root.join("rust-sdk"))
        .status();

    assert!(db_path.exists());
    fs::remove_file(&db_path).unwrap();
}
