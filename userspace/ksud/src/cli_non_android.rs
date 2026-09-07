use anyhow::Result;
use clap::Parser;

#[cfg(target_os = "windows")]
use anyhow::{Context, bail};
#[cfg(target_os = "windows")]
use chrono::{Datelike, Local, Timelike};
#[cfg(target_os = "windows")]
use std::io::{self, Write};

use crate::boot_patch::{BootPatchArgs, BootRestoreArgs};
use crate::lkm_image::BootPatchV2Args;
use crate::{apk_sign, defs};

/// KernelSU cli for non-android
#[derive(Parser, Debug)]
#[command(author, version = defs::VERSION_NAME, about, long_about = None)]
struct Args {
    #[command(subcommand)]
    command: Commands,
}

#[derive(clap::Subcommand, Debug)]
enum Commands {
    /// Patch boot or init_boot images to apply KernelSU
    BootPatch(BootPatchArgs),

    /// Restore boot or init_boot images patched by KernelSU
    BootRestore(BootRestoreArgs),

    /// Patch KernelSU into a boot image
    ///
    /// Always operates on a boot image; never selects init_boot or vendor_boot.
    BootPatchV2(BootPatchV2Args),

    /// Get apk size and hash
    GetSign {
        /// apk path
        apk: String,
    },

    /// show supported kmi versions
    SupportedKmis,
}

pub fn run() -> Result<()> {
    env_logger::init();

    #[cfg(target_os = "windows")]
    if std::env::args_os().len() == 1 {
        return run_windows_image_patcher();
    }

    let cli = Args::parse();

    log::info!("command: {:?}", cli.command);

    let result = match cli.command {
        Commands::GetSign { apk } => {
            let sign = apk_sign::get_apk_signature(&apk)?;
            println!("size: {:#x}, hash: {}", sign.0, sign.1);
            Ok(())
        }

        Commands::BootPatch(boot_patch) => crate::boot_patch::patch(boot_patch),

        Commands::BootRestore(boot_restore) => crate::boot_patch::restore(boot_restore),

        Commands::BootPatchV2(patch) => crate::lkm_image::patch_boot(&patch),

        Commands::SupportedKmis => {
            let kmi = crate::assets::list_supported_kmi();
            for kmi in &kmi {
                println!("{kmi}");
            }
            Ok(())
        }
    };

    if let Err(e) = &result {
        log::error!("Error: {e:?}");
    }
    result
}

#[cfg(target_os = "windows")]
fn arm64_kmis() -> Vec<String> {
    let mut kmis = crate::assets::list_supported_kmi()
        .into_iter()
        .filter_map(|name| {
            name.strip_prefix("aarch64/")
                .or_else(|| name.strip_prefix("aarch64\\"))
                .map(ToOwned::to_owned)
        })
        .filter(|name| name.starts_with("android"))
        .collect::<Vec<_>>();
    kmis.sort();
    kmis.dedup();
    kmis
}

#[cfg(target_os = "windows")]
fn wait_for_enter() {
    println!();
    print!("按回车键退出...");
    let _ = io::stdout().flush();
    let mut input = String::new();
    let _ = io::stdin().read_line(&mut input);
}

#[cfg(target_os = "windows")]
fn run_windows_image_patcher() -> Result<()> {
    const UTF8_CODE_PAGE: u32 = 65001;
    // SAFETY: These Windows APIs only change the code page of this console.
    unsafe {
        windows_sys::Win32::System::Console::SetConsoleCP(UTF8_CODE_PAGE);
        windows_sys::Win32::System::Console::SetConsoleOutputCP(UTF8_CODE_PAGE);
    }

    let result = run_windows_image_patcher_inner();
    if let Err(error) = &result {
        eprintln!();
        eprintln!("修补失败：{error:#}");
    }
    wait_for_enter();
    result
}

#[cfg(target_os = "windows")]
fn run_windows_image_patcher_inner() -> Result<()> {
    println!("============================================================");
    println!("                 yzhlSU 本地镜像工坊");
    println!("============================================================");
    println!("只生成新镜像；不会连接设备，不会调用 ADB/Fastboot，也不会刷入。\n");

    let kmis = arm64_kmis();
    if kmis.is_empty() {
        bail!("当前 EXE 没有内置任何 ARM64 KMI 模块");
    }

    println!("请选择设备内核对应的 KMI：");
    for (index, kmi) in kmis.iter().enumerate() {
        println!("  {}. {kmi}", index + 1);
    }
    println!();
    print!("输入序号并回车：");
    io::stdout().flush().context("无法刷新控制台输出")?;

    let mut choice = String::new();
    io::stdin()
        .read_line(&mut choice)
        .context("无法读取输入的序号")?;
    let selected = choice
        .trim()
        .parse::<usize>()
        .context("请输入列表中的数字序号")?;
    let kmi = kmis
        .get(selected.checked_sub(1).context("序号必须从 1 开始")?)
        .cloned()
        .context("输入的序号不在列表范围内")?;

    println!("已选择：{kmi}");
    println!("正在打开文件选择窗口，请选择匹配当前设备和系统版本的原始镜像...");

    let Some(image) = rfd::FileDialog::new()
        .set_title("选择原始 boot 或 init_boot 镜像")
        .add_filter("Android 镜像", &["img"])
        .pick_file()
    else {
        println!("已取消选择，没有生成任何文件。");
        return Ok(());
    };

    let image = image
        .canonicalize()
        .with_context(|| format!("无法读取镜像：{}", image.display()))?;
    let metadata = std::fs::metadata(&image)
        .with_context(|| format!("无法读取镜像信息：{}", image.display()))?;
    if metadata.len() == 0 {
        bail!("选择的镜像是空文件");
    }

    let parent = image.parent().context("无法确定原始镜像所在目录")?;
    let now = Local::now();
    let folder_name = format!(
        "{}.{}.{}.{}.{}",
        now.year(),
        now.month(),
        now.day(),
        now.hour(),
        now.minute()
    );
    let output_dir = parent.join(folder_name);
    std::fs::create_dir_all(&output_dir)
        .with_context(|| format!("无法创建输出目录：{}", output_dir.display()))?;

    let output_name = format!("yzhlSU_patched_{kmi}.img");
    let output_image = output_dir.join(&output_name);
    if output_image.exists() {
        bail!(
            "输出文件已经存在，为防止覆盖已停止：{}",
            output_image.display()
        );
    }

    println!("原始镜像：{}", image.display());
    println!("输出目录：{}", output_dir.display());
    println!("开始修补，请勿关闭窗口...\n");

    let source_hash = sha256::digest(
        std::fs::read(&image)
            .with_context(|| format!("无法读取镜像：{}", image.display()))?,
    );
    crate::boot_patch::patch_local_image(
        image.clone(),
        kmi.clone(),
        output_dir.clone(),
        output_name,
    )?;

    if !output_image.is_file() {
        bail!("修补引擎没有生成预期的输出镜像");
    }
    let output_hash = sha256::digest(
        std::fs::read(&output_image)
            .with_context(|| format!("无法读取输出镜像：{}", output_image.display()))?,
    );
    let report = format!(
        "yzhlSU 本地镜像修补报告\r\n\
         KMI: {kmi}\r\n\
         原始镜像: {}\r\n\
         原始镜像 SHA256: {source_hash}\r\n\
         修补镜像: {}\r\n\
         修补镜像 SHA256: {output_hash}\r\n\
         生成时间: {}\r\n",
        image.display(),
        output_image.display(),
        now.format("%Y-%m-%d %H:%M:%S %:z")
    );
    let report_path = output_dir.join("SHA256.txt");
    std::fs::write(&report_path, report.as_bytes())
        .with_context(|| format!("无法写入校验报告：{}", report_path.display()))?;

    println!();
    println!("修补成功！");
    println!("输出镜像：{}", output_image.display());
    println!("SHA-256：{output_hash}");
    println!("校验报告：{}", report_path.display());
    println!("工具没有连接或刷写任何设备。");
    Ok(())
}
