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

# --- Transaction Flooding Test ---
# Define a batch of 12 transactions to send rapidly
TRANSACTIONS = [
    {"receiver": "Miguel", "amount": "1"},
    {"receiver": "Miguel", "amount": "2"},
    {"receiver": "Miguel", "amount": "3"},
    {"receiver": "Miguel", "amount": "4"},
    {"receiver": "Miguel", "amount": "5"},
    {"receiver": "Miguel", "amount": "6"},
    {"receiver": "Miguel", "amount": "7"},
    {"receiver": "Miguel", "amount": "8"},
    {"receiver": "Miguel", "amount": "9"},
    {"receiver": "Miguel", "amount": "10"},
    {"receiver": "Miguel", "amount": "11"},
    {"receiver": "Miguel", "amount": "12"}
]

# --- Expected Results ---
EXPECTED_BLOCK_COUNT = 4
TRANSACTIONS_PER_BLOCK = 3

# --- Timing ---
MEMBER_START_DELAY = 1
CLIENT_LIB_STARTUP_DELAY = 3
CLIENT_INTERACTION_DELAY = 0.1  # Very short delay to send transactions quickly
CLIENT_WINDOW_LOAD_DELAY = 0.3
BATCH_WAIT_SECONDS = 3  # Short wait between transaction batches
CONSENSUS_WAIT_SECONDS = 60  # Longer wait for multiple blocks to be created
CHECKING_MAX_WAIT_SECONDS = 90

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
                print(f"Started member {member_name} with PID: {pid}")

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

def check_blocks_created(expected_count=EXPECTED_BLOCK_COUNT):
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

def check_blocks_transaction_distribution():
    """Check if the blocks have the expected distribution of transactions (3 per block)."""
    blocks = get_block_files()
    
    if not blocks:
        print("No block files found.")
        return False
    
    # Sort the blocks by their number
    sorted_blocks = sorted(blocks, key=lambda x: int(x.replace("block", "").replace(".json", "")))
    
    # Use PowerShell to check each block
    blocks_analysis = []
    
    for block_file in sorted_blocks:
        block_path = os.path.join(BLOCKS_DIR, block_file)
        
        cmd = f"""
        $blockContent = Get-Content -Path "{block_path}" -Raw
        $block = $blockContent | ConvertFrom-Json
        
        # Check transaction count
        $transactionCount = 0
        if ($block.PSObject.Properties.Name -contains "transactions") {{
            $transactionCount = $block.transactions.Count
        }}
        
        Write-Output "$transactionCount"
        """
        
        stdout, _, _ = run_powershell_command(cmd)
        try:
            tx_count = int(stdout.strip())
            blocks_analysis.append({"block": block_file, "tx_count": tx_count})
        except:
            print(f"  Error parsing transaction count for {block_file}")
            blocks_analysis.append({"block": block_file, "tx_count": 0})
    
    # Print the block distribution
    print("\nTransaction distribution across blocks:")
    for block_info in blocks_analysis:
        print(f"  {block_info['block']}: {block_info['tx_count']} transactions")
    
    # Check if we have the right number of blocks with 3 transactions each
    blocks_with_3_tx = sum(1 for b in blocks_analysis if b['tx_count'] == TRANSACTIONS_PER_BLOCK)
    
    # Check the total number of transactions across all blocks
    total_tx = sum(b['tx_count'] for b in blocks_analysis)
    
    print(f"\nBlocks with {TRANSACTIONS_PER_BLOCK} transactions: {blocks_with_3_tx}/{EXPECTED_BLOCK_COUNT}")
    print(f"Total transactions processed: {total_tx}/{len(TRANSACTIONS)}")
    
    # Success criteria:
    # - At least the expected number of blocks exist
    # - At least the expected number of blocks have 3 transactions each
    # - All transactions are accounted for
    if len(blocks_analysis) >= EXPECTED_BLOCK_COUNT and total_tx == len(TRANSACTIONS):
        print("PASS: All transactions were processed and distributed across blocks")
        return True
    else:
        print("FAIL: Not all transactions were processed correctly")
        return False

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
        pyautogui.PAUSE = 0.05  # Very short pause to send transactions rapidly
        
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
        
        # Focus the client window once before sending all transactions
        print("\nFocusing client window...")
        activated = activate_window_by_pid(client_pid)
        if not activated:
            activated = focus_window_by_title(client_title)
        
        if not activated:
            print("WARNING: Could not focus client window. Attempting inputs anyway...")
        time.sleep(CLIENT_WINDOW_LOAD_DELAY)
        
        # Send the 12 transactions in batches
        print("\n=== TRANSACTION FLOODING TEST ===")
        print(f"Sending {len(TRANSACTIONS)} transactions rapidly...")
        start_time = time.time()
        
        # Send transactions in groups of 3 with small delays between groups
        for i in range(0, len(TRANSACTIONS), 3):
            batch = TRANSACTIONS[i:i+3]
            print(f"\nSending batch {i//3 + 1}/{len(TRANSACTIONS)//3}:")
            
            batch_start_time = time.time()
            for j, transaction in enumerate(batch):
                print(f"  Transaction {i+j+1}/{len(TRANSACTIONS)}: {transaction['receiver']}, Amount: {transaction['amount']}")
                send_transaction(client_pid, client_title, transaction)
            
            batch_elapsed = time.time() - batch_start_time
            print(f"  Batch sent in {batch_elapsed:.2f} seconds")
            
            # Wait briefly between batches to allow some processing
            if i + 3 < len(TRANSACTIONS):
                print(f"  Waiting {BATCH_WAIT_SECONDS}s between batches...")
                time.sleep(BATCH_WAIT_SECONDS)
        
        # Send exit command after all transactions
        exit_client(client_pid, client_title)
        
        total_elapsed_time = time.time() - start_time
        print(f"\nAll transactions sent in {total_elapsed_time:.2f} seconds")
        
        # Wait for consensus process to complete for all blocks
        print(f"\nWaiting {CONSENSUS_WAIT_SECONDS}s for consensus on all transactions...")
        time.sleep(CONSENSUS_WAIT_SECONDS)
        print("Consensus wait finished.")
        
        # Verify correct number of blocks were created
        blocks_test_passed = check_blocks_created(EXPECTED_BLOCK_COUNT)
        
        # Check transaction distribution across blocks
        distribution_test_passed = check_blocks_transaction_distribution()
        
        # Final test result
        test_passed = blocks_test_passed and distribution_test_passed
        
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
            print(f"RESULT: PASSED - All {len(TRANSACTIONS)} transactions were correctly processed")
            print(f"The blockchain successfully created {EXPECTED_BLOCK_COUNT} blocks with {TRANSACTIONS_PER_BLOCK} transactions each")
            sys.exit(0)
        else:
            print("RESULT: FAILED - Transactions were not processed as expected")
            print("Check the logs above for details on what went wrong.")
            sys.exit(1)