import subprocess
import time
import json
import sys
import os
import shutil
import pyautogui
import psutil

# --- Configuration ---
PROJECT_ROOT = os.getcwd()
PRIV_KEYS_DIR = os.path.join(PROJECT_ROOT, "src/main/resources/priv_keys")
PUB_KEYS_DIR = os.path.join(PROJECT_ROOT, "src/main/resources/pub_keys")
BLOCKS_DIR = os.path.join(PROJECT_ROOT, "src/main/resources/blocks")
LATEST_BLOCK_FILE = os.path.join(BLOCKS_DIR, "block1.json")

# --- Invalid Transaction Test ---
# Define an excessively large transaction amount that should be rejected
INVALID_TRANSACTION = {
    "action": "transaction",
    "receiver": "jiraiya",
    "amount": "10000"  # Excessively large amount that should be rejected
}

# --- Timing ---
MEMBER_START_DELAY = 1
CLIENT_LIB_STARTUP_DELAY = 3
CLIENT_INTERACTION_DELAY = 1
CLIENT_WINDOW_LOAD_DELAY = 2
CONSENSUS_WAIT_SECONDS = 30
CHECKING_POLL_INTERVAL_SECONDS = 5
CHECKING_MAX_WAIT_SECONDS = 60

# --- Process Management ---
process_ids = []

def run_powershell_command(command):
    """Run a PowerShell command and return its output."""
    print(f"Running PowerShell command: {command}")
    process = subprocess.Popen(
        ["powershell", "-Command", command],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    stdout, stderr = process.communicate()
    
    if process.returncode != 0:
        print(f"PowerShell command failed with exit code {process.returncode}")
        print(f"Error: {stderr}")
    
    return stdout, stderr, process.returncode

def clean_directories():
    """Clean key directories and all block files."""
    print("Cleaning key and blocks directories...")
    
    # Use PowerShell to clean directories
    cmd = """
    # Clean key directories
    Remove-Item -Path "src\\main\\resources\\priv_keys\\*" -Force -Recurse -ErrorAction SilentlyContinue
    Remove-Item -Path "src\\main\\resources\\pub_keys\\*" -Force -Recurse -ErrorAction SilentlyContinue
    
    # Clean blocks directory (all block files)
    Remove-Item -Path "src\\main\\resources\\blocks\\*.json" -Force -ErrorAction SilentlyContinue
    
    # Create directories if they don't exist
    New-Item -Path "src\\main\\resources\\priv_keys" -ItemType Directory -Force | Out-Null
    New-Item -Path "src\\main\\resources\\pub_keys" -ItemType Directory -Force | Out-Null
    New-Item -Path "src\\main\\resources\\blocks" -ItemType Directory -Force | Out-Null
    """
    
    run_powershell_command(cmd)
    
    # Record initial block state
    return get_block_files()

def get_block_files():
    """Get a list of block files in the blocks directory using PowerShell."""
    cmd = """
    if (Test-Path "src\\main\\resources\\blocks") {
        Get-ChildItem -Path "src\\main\\resources\\blocks" -Filter "block*.json" | Select-Object -ExpandProperty Name
    }
    """
    stdout, _, _ = run_powershell_command(cmd)
    files = [line.strip() for line in stdout.splitlines() if line.strip()]
    return files

def start_members():
    """Start the blockchain members with one Byzantine YES_MAN."""
    print("Starting members...")
    
    # Define member configurations
    members_cmd = """
    # Array of member configurations (name, behavior)
    $members = @(
        @{name="member1"; behavior="default"},
        @{name="member2"; behavior="default"},
        @{name="member3"; behavior="default"},
        @{name="member4"; behavior="YES_MAN"}  # Byzantine YesMan behavior
    )
    
    # Start each member in a new terminal
    foreach ($member in $members) {
        if ($member.behavior -eq "default") {
            # Start a normal member
            $process = Start-Process cmd.exe -ArgumentList "/K title $($member.name) && mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=`"$($member.name)`"" -PassThru
            Write-Output $process.Id
        } else {
            # Start a Byzantine member with behavior
            $process = Start-Process cmd.exe -ArgumentList "/K title $($member.name)-$($member.behavior) && mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=`"$($member.name) $($member.behavior)`"" -PassThru
            Write-Output $process.Id
        }
        Start-Sleep -Seconds 1
    }
    """
    
    stdout, _, _ = run_powershell_command(members_cmd)
    for line in stdout.splitlines():
        if line.strip() and line.strip().isdigit():
            process_ids.append(int(line.strip()))
            print(f"Started member process with PID: {line.strip()}")

def start_client_library():
    """Start the client library."""
    print("Starting client library...")
    
    cmd = """
    $process = Start-Process cmd.exe -ArgumentList "/K title ClientLibrary && mvn exec:java -Dexec.mainClass=com.depchain.client.ClientLibrary -Dexec.args=8080" -PassThru
    Write-Output $process.Id
    """
    
    stdout, _, _ = run_powershell_command(cmd)
    for line in stdout.splitlines():
        if line.strip() and line.strip().isdigit():
            process_ids.append(int(line.strip()))
            print(f"Started client library process with PID: {line.strip()}")

def start_client(client_name):
    """Start a client and return its window title for automation."""
    print(f"Starting client: {client_name}...")
    
    cmd = f"""
    $process = Start-Process cmd.exe -ArgumentList "/K title {client_name} && mvn exec:java -Dexec.mainClass=com.depchain.client.Client -Dexec.args=`"{client_name}`"" -PassThru
    Write-Output $process.Id
    """
    
    stdout, _, _ = run_powershell_command(cmd)
    client_pid = None
    for line in stdout.splitlines():
        if line.strip() and line.strip().isdigit():
            client_pid = int(line.strip())
            process_ids.append(client_pid)
            print(f"Started client process with PID: {client_pid}")
    
    return client_pid, client_name

def focus_window_by_title(title):
    """Focus a window by its title."""
    cmd = f"""
    Add-Type @"
    using System;
    using System.Runtime.InteropServices;
    public class Win32 {{
        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool SetForegroundWindow(IntPtr hWnd);
        
        [DllImport("user32.dll")]
        public static extern IntPtr FindWindow(string lpClassName, string lpWindowName);
    }}
"@

    $windowHandle = [Win32]::FindWindow($null, "{title}")
    if ($windowHandle -ne [IntPtr]::Zero) {{
        [Win32]::SetForegroundWindow($windowHandle)
        return $true
    }} else {{
        return $false
    }}
    """
    
    stdout, _, _ = run_powershell_command(cmd)
    return "True" in stdout

def activate_window_by_pid(pid):
    """Use PowerShell to focus a window by process ID."""
    cmd = f"""
    Add-Type @"
    using System;
    using System.Runtime.InteropServices;
    using System.Diagnostics;
    
    public class WindowHelper {{
        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool SetForegroundWindow(IntPtr hWnd);
        
        public static bool ActivateProcessWindow(int processId) {{
            Process process = Process.GetProcessById(processId);
            if (process != null && process.MainWindowHandle != IntPtr.Zero) {{
                return SetForegroundWindow(process.MainWindowHandle);
            }}
            return false;
        }}
    }}
"@

    $result = [WindowHelper]::ActivateProcessWindow({pid})
    Write-Output $result
    """
    
    stdout, _, _ = run_powershell_command(cmd)
    return "True" in stdout

def send_automated_inputs(client_pid, client_title):
    """Send automated inputs to the client window using PyAutoGUI."""
    print(f"Automating inputs to {client_title} (PID: {client_pid})...")
    
    # First try focusing by PID
    activated = activate_window_by_pid(client_pid)
    if not activated:
        # Try focusing by window title
        activated = focus_window_by_title(client_title)
    
    if not activated:
        print(f"Warning: Could not focus client window. Attempting inputs anyway...")
    
    # Allow window to fully load and gain focus
    time.sleep(CLIENT_WINDOW_LOAD_DELAY)
    
    # Send transaction command
    print("  Sending: 1 (Append Data)")
    pyautogui.typewrite("1")
    pyautogui.press("enter")
    time.sleep(CLIENT_INTERACTION_DELAY)
    
    # Send receiver
    print(f"  Sending Receiver: {INVALID_TRANSACTION['receiver']}")
    pyautogui.typewrite(INVALID_TRANSACTION['receiver'])
    pyautogui.press("enter")
    time.sleep(CLIENT_INTERACTION_DELAY)
    
    # Send invalid amount
    print(f"  Sending Invalid Amount: {INVALID_TRANSACTION['amount']}")
    pyautogui.typewrite(INVALID_TRANSACTION['amount'])
    pyautogui.press("enter")
    time.sleep(CLIENT_INTERACTION_DELAY)
    
    # Send exit command
    print("  Sending: 0 (Exit)")
    pyautogui.typewrite("0")
    pyautogui.press("enter")
    
    print("Automated inputs complete.")

def check_no_new_blocks(blocks_before):
    """Verify that no new block files have been added."""
    current_blocks = get_block_files()
    print(f"Checking if any new blocks were added...")
    print(f"  Blocks before: {blocks_before}")
    print(f"  Blocks now: {current_blocks}")
    
    # Compare block sets (names only)
    blocks_before_set = set(blocks_before)
    current_blocks_set = set(current_blocks)
    
    if len(current_blocks_set) > len(blocks_before_set):
        new_blocks = [b for b in current_blocks if b not in blocks_before_set]
        print(f"  FAIL: New blocks were added: {new_blocks}")
        return False
    else:
        print("  PASS: No new blocks were added (invalid transaction was rejected)")
        return True

def terminate_all_processes():
    """Terminate all processes started by this script."""
    print("\nTerminating all processes...")
    
    for pid in process_ids:
        try:
            process = psutil.Process(pid)
            # Terminate the process and all its children
            for child in process.children(recursive=True):
                try:
                    child.terminate()
                except:
                    pass
            process.terminate()
            print(f"  Terminated process with PID: {pid}")
        except psutil.NoSuchProcess:
            print(f"  Process with PID {pid} already terminated")
        except Exception as e:
            print(f"  Error terminating process with PID {pid}: {e}")
    
    # Clean up any remaining CMD windows that might be running Maven
    cmd = """
    Get-Process | Where-Object {$_.ProcessName -eq "cmd" -and $_.MainWindowTitle -match "member|client|ClientLibrary"} | ForEach-Object {
        $_ | Stop-Process -Force
        Write-Output "Terminated: $($_.MainWindowTitle) (PID: $($_.Id))"
    }
    """
    
    stdout, _, _ = run_powershell_command(cmd)
    for line in stdout.splitlines():
        if line.strip():
            print(f"  {line.strip()}")

# --- Main Execution ---
if __name__ == "__main__":
    try:
        # Check required libraries
        try:
            import pyautogui
            import psutil
        except ImportError:
            print("This script requires pyautogui and psutil. Installing...")
            subprocess.check_call([sys.executable, "-m", "pip", "install", "pyautogui", "psutil"])
            import pyautogui
            import psutil
            print("Libraries installed successfully.")
        
        # Set pyautogui pause between actions
        pyautogui.PAUSE = 0.5
        
        # Clean directories (including blocks directory)
        clean_directories()
        
        # Verify blocks directory is empty
        initial_blocks = get_block_files()
        if initial_blocks:
            print(f"Warning: Block files still exist after cleaning: {initial_blocks}")
        else:
            print("Confirmed: Blocks directory is empty")
        
        # Start members with one Byzantine YES_MAN
        start_members()
        time.sleep(MEMBER_START_DELAY * 4)
        
        # Start client library
        start_client_library()
        print(f"Waiting {CLIENT_LIB_STARTUP_DELAY}s for ClientLibrary...")
        time.sleep(CLIENT_LIB_STARTUP_DELAY)
        
        # Start client1
        client_pid, client_title = start_client("client1")
        time.sleep(2)
        
        # Send automated inputs to client1
        send_automated_inputs(client_pid, client_title)
        
        # Wait for consensus process
        print(f"\nWaiting {CONSENSUS_WAIT_SECONDS} seconds for consensus process...")
        time.sleep(CONSENSUS_WAIT_SECONDS)
        print("Consensus wait finished.")
        
        # Verify no blocks were added
        test_passed = check_no_new_blocks(initial_blocks)
        
    except KeyboardInterrupt:
        print("\nTest interrupted by user.")
        test_passed = False
    except Exception as e:
        print(f"\nAn unexpected error occurred during test execution: {e}")
        import traceback
        traceback.print_exc()
        test_passed = False
    finally:
        # Terminate all processes
        terminate_all_processes()
        
        # Final Result
        print("\n----- Test Summary -----")
        if test_passed:
            print("RESULT: PASSED - Invalid transaction was correctly rejected")
            print("The blockchain correctly rejected the transaction with an excessive amount.")
            sys.exit(0)
        else:
            print("RESULT: FAILED - Invalid transaction was incorrectly processed")
            print("A new block was created despite the invalid transaction amount.")
            sys.exit(1)