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

# --- Member Omission Test ---
# Define valid transaction
TRANSACTION = {"receiver": "Miguel", "amount": "25"}

# --- Timing ---
MEMBER_START_DELAY = 1
CLIENT_LIB_STARTUP_DELAY = 5
CLIENT_INTERACTION_DELAY = 0.2
CLIENT_WINDOW_LOAD_DELAY = 0.5
CONSENSUS_WAIT_SECONDS = 30
STABILIZATION_WAIT_SECONDS = 5
CHECKING_MAX_WAIT_SECONDS = 60

# --- Process Management ---
process_ids = []
member_processes = {}  # To track member processes specifically

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
    """Start the blockchain members (all normal)."""
    print("Starting members...")
    
    # Define member configurations
    members_cmd = """
    # Array of member configurations
    $members = @(
        @{name="member1"; behavior="default"},
        @{name="member2"; behavior="default"},
        @{name="member3"; behavior="default"},
        @{name="member4"; behavior="default"}
    )
    
    # Start each member in a new terminal
    foreach ($member in $members) {
        $process = Start-Process cmd.exe -ArgumentList "/K title $($member.name) && mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=`"$($member.name)`"" -PassThru
        Write-Output "$($member.name):$($process.Id)"
        Start-Sleep -Seconds 1
    }
    """
    
    stdout, _, _ = run_powershell_command(members_cmd)
    for line in stdout.splitlines():
        if line.strip() and ":" in line:
            parts = line.strip().split(":")
            if len(parts) == 2 and parts[1].isdigit():
                member_name = parts[0]
                pid = int(parts[1])
                process_ids.append(pid)
                member_processes[member_name] = pid
                print(f"Started member {member_name} with PID: {pid}")

def terminate_member(member_name):
    """Terminate a specific member process."""
    if member_name not in member_processes:
        print(f"Warning: Member {member_name} not found in process list")
        return False
    
    pid = member_processes[member_name]
    print(f"Terminating member {member_name} (PID: {pid})...")
    
    try:
        process = psutil.Process(pid)
        # Terminate the process and all its children
        for child in process.children(recursive=True):
            try:
                child.terminate()
            except:
                pass
        process.terminate()
        print(f"  Successfully terminated {member_name} (PID: {pid})")
        
        # Also use PowerShell to ensure termination
        cmd = f"""
        Stop-Process -Id {pid} -Force -ErrorAction SilentlyContinue
        """
        run_powershell_command(cmd)
        
        return True
    except psutil.NoSuchProcess:
        print(f"  Process with PID {pid} already terminated")
        return True
    except Exception as e:
        print(f"  Error terminating process with PID {pid}: {e}")
        return False

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

def send_transaction(client_pid, client_title, transaction):
    """Send a transaction to the client window using PyAutoGUI."""
    # First try focusing by PID
    activated = activate_window_by_pid(client_pid)
    if not activated:
        # Try focusing by window title
        activated = focus_window_by_title(client_title)
    
    if not activated:
        print(f"Warning: Could not focus client window. Attempting inputs anyway...")
    
    # Allow window to gain focus
    time.sleep(CLIENT_WINDOW_LOAD_DELAY)
    
    # Send transaction command
    pyautogui.typewrite("1")
    pyautogui.press("enter")
    time.sleep(CLIENT_INTERACTION_DELAY)
    
    # Send receiver
    pyautogui.typewrite(transaction['receiver'])
    pyautogui.press("enter")
    time.sleep(CLIENT_INTERACTION_DELAY)
    
    # Send amount
    pyautogui.typewrite(transaction['amount'])
    pyautogui.press("enter")
    time.sleep(CLIENT_INTERACTION_DELAY)

def exit_client(client_pid, client_title):
    """Send exit command to client window."""
    # Focus the window
    activated = activate_window_by_pid(client_pid)
    if not activated:
        activated = focus_window_by_title(client_title)
    
    time.sleep(CLIENT_WINDOW_LOAD_DELAY)
    
    # Send exit command
    pyautogui.typewrite("0")
    pyautogui.press("enter")

def check_blocks_created(expected_count=1):
    """Verify that the expected number of blocks were created."""
    blocks = get_block_files()
    print(f"Checking for block files...")
    print(f"  Found block files: {blocks}")
    
    if len(blocks) >= expected_count:
        print(f"  PASS: Found {len(blocks)} block(s), expected at least {expected_count}")
        return True
    else:
        print(f"  FAIL: Found only {len(blocks)} block(s), expected at least {expected_count}")
        return False

def check_latest_block_for_transaction():
    """Check the latest block to verify it contains our transaction in the expected format."""
    blocks = get_block_files()
    
    if not blocks:
        print("No block files found.")
        return False
    
    # Sort the blocks by their number (extract the number from "blockX.json")
    sorted_blocks = sorted(blocks, key=lambda x: int(x.replace("block", "").replace(".json", "")))
    latest_block = sorted_blocks[-1]
    latest_block_path = os.path.join(BLOCKS_DIR, latest_block)
    
    print(f"Checking latest block: {latest_block}")
    
    # Use PowerShell to read and parse the block file
    cmd = f"""
    $blockContent = Get-Content -Path "{latest_block_path}" -Raw
    $block = $blockContent | ConvertFrom-Json
    
    # Check for block hash fields
    if (-not ($block.PSObject.Properties.Name -contains "block_hash" -and 
              $block.PSObject.Properties.Name -contains "previous_block_hash")) {{
        Write-Output "FAIL: Missing required hash fields"
        return
    }}
    
    # Check for transactions
    if (-not ($block.PSObject.Properties.Name -contains "transactions")) {{
        Write-Output "FAIL: Missing transactions array"
        return
    }}
    
    # Check transaction count
    $transactions = $block.transactions
    $transactionCount = $transactions.Count
    Write-Output "Transaction count: $transactionCount"
    
    if ($transactionCount -lt 1) {{
        Write-Output "FAIL: Expected at least 1 transaction, found $transactionCount"
        return
    }}
    
    # Check transaction details
    $tx = $transactions[$transactions.Count - 1]  # Get the last transaction
    
    $tx_valid = $tx.sender -eq "client1" -and $tx.receiver -eq "{TRANSACTION['receiver']}" -and [math]::Abs($tx.amount - {TRANSACTION['amount']}) -lt 1
    
    Write-Output "Transaction: sender=$($tx.sender), receiver=$($tx.receiver), amount=$($tx.amount) - Valid: $tx_valid"
    
    # Check for state
    if (-not ($block.PSObject.Properties.Name -contains "state")) {{
        Write-Output "FAIL: Missing state object"
        return
    }}
    
    # Check state entries count (should have at least some entries)
    $stateKeys = $block.state.PSObject.Properties.Name
    $stateCount = $stateKeys.Count
    Write-Output "State entries: $stateCount"
    
    if ($stateCount -lt 3) {{
        Write-Output "FAIL: Expected at least 3 state entries"
        return
    }}
    
    # Overall validation
    if ($tx_valid -and $stateCount -ge 3) {{
        Write-Output "SUCCESS: Block structure matches expected format"
    }} else {{
        Write-Output "FAIL: Block structure does not match expected format"
    }}
    """
    
    stdout, _, _ = run_powershell_command(cmd)
    print(f"Block content analysis:")
    for line in stdout.splitlines():
        if line.strip():
            print(f"  {line.strip()}")
    
    return "SUCCESS" in stdout

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
        pyautogui.PAUSE = 0.1
        
        # Clean directories (including blocks directory)
        clean_directories()
        
        # Verify blocks directory is empty
        initial_blocks = get_block_files()
        if initial_blocks:
            print(f"Warning: Block files still exist after cleaning: {initial_blocks}")
        else:
            print("Confirmed: Blocks directory is empty")
        
        # Start all 4 members
        start_members()
        print(f"Started all 4 members. Waiting for initialization...")
        time.sleep(MEMBER_START_DELAY * 4)
        
        # Start client library
        start_client_library()
        print(f"Waiting {CLIENT_LIB_STARTUP_DELAY}s for ClientLibrary...")
        time.sleep(CLIENT_LIB_STARTUP_DELAY)
        
        # Start client1
        client_pid, client_title = start_client("client1")
        print("Waiting for client to initialize...")
        time.sleep(3)
        
        # Terminate member4 (omission test)
        print("\n=== OMISSION TEST ===")
        print("Now terminating member4 to simulate node failure...")
        member_terminated = terminate_member("member4")
        
        if not member_terminated:
            print("WARNING: Could not terminate member4, test may not be valid")
        
        print(f"Waiting {STABILIZATION_WAIT_SECONDS}s for network to stabilize after member omission...")
        time.sleep(STABILIZATION_WAIT_SECONDS)
        
        # Send the transaction
        print("\nSending transaction after member omission:")
        print(f"Transaction details: receiver={TRANSACTION['receiver']}, amount={TRANSACTION['amount']}")
        start_time = time.time()
        
        send_transaction(client_pid, client_title, TRANSACTION)
        
        elapsed_time = time.time() - start_time
        print(f"Transaction sent in {elapsed_time:.2f} seconds")
        
        # Send exit command
        exit_client(client_pid, client_title)
        
        # Wait for consensus process
        print(f"\nWaiting {CONSENSUS_WAIT_SECONDS}s for consensus process...")
        time.sleep(CONSENSUS_WAIT_SECONDS)
        print("Consensus wait finished.")
        
        # Verify blocks were created
        test_passed = check_blocks_created(expected_count=1)
        
        # Check transaction details in the block
        if test_passed:
            transaction_check = check_latest_block_for_transaction()
            test_passed = test_passed and transaction_check
        
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
            print("RESULT: PASSED - Transaction was successfully processed despite member omission")
            print("The blockchain correctly handled the absence of one member (n=4, f=1).")
            sys.exit(0)
        else:
            print("RESULT: FAILED - Transaction was not processed as expected after member omission")
            print("Check the logs above for details on what went wrong.")
            sys.exit(1)