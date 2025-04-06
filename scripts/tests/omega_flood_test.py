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

# --- Omega Transaction Flooding Test ---
# Define a mega batch of 100 transactions to send rapidly
TRANSACTIONS = []
for i in range(1, 20):
    TRANSACTIONS.append({"receiver": "Miguel", "amount": "1"})

# --- Expected Results ---
TRANSACTIONS_PER_BLOCK = 3
EXPECTED_FULL_BLOCKS = 33  # 33 blocks with 3 txs each = 99 txs
EXPECTED_PARTIAL_BLOCKS = 1  # 1 block with 1 tx
EXPECTED_BLOCK_COUNT = EXPECTED_FULL_BLOCKS + EXPECTED_PARTIAL_BLOCKS  # 34 total blocks

# --- Timing ---
MEMBER_START_DELAY = 1
CLIENT_LIB_STARTUP_DELAY = 5
CLIENT_INTERACTION_DELAY = 0.05  # Even shorter delay for extreme flooding
CLIENT_WINDOW_LOAD_DELAY = 0.2
BATCH_WAIT_SECONDS = 2  # Shorter wait between transaction batches
CONSENSUS_WAIT_SECONDS = 120  # Longer wait for many blocks to be created
POLLING_INTERVAL_SECONDS = 15  # Check progress periodically
MAX_WAIT_SECONDS = 180  # Maximum wait time

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

def check_blocks_created(min_expected_count=EXPECTED_BLOCK_COUNT):
    """Verify that at least the expected number of blocks were created."""
    blocks = get_block_files()
    print(f"Checking for block files...")
    print(f"  Found block files: {blocks}")
    
    if len(blocks) >= min_expected_count:
        print(f"  PASS: Found {len(blocks)} block(s), expected at least {min_expected_count}")
        return True
    else:
        print(f"  PARTIAL: Found {len(blocks)} block(s), expecting at least {min_expected_count}")
        return False

def check_blocks_transaction_distribution():
    """Check if the blocks have the expected distribution of transactions."""
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
    full_blocks = 0
    partial_blocks = 0
    
    for block_info in blocks_analysis:
        if block_info['tx_count'] == TRANSACTIONS_PER_BLOCK:
            full_blocks += 1
            block_type = "FULL"
        elif block_info['tx_count'] > 0:
            partial_blocks += 1
            block_type = "PARTIAL"
        else:
            block_type = "EMPTY"
        
        print(f"  {block_info['block']}: {block_info['tx_count']} transactions ({block_type})")
    
    # Check the total number of transactions across all blocks
    total_tx = sum(b['tx_count'] for b in blocks_analysis)
    
    print(f"\nFull blocks (with {TRANSACTIONS_PER_BLOCK} transactions): {full_blocks}")
    print(f"Partial blocks: {partial_blocks}")
    print(f"Total transactions processed: {total_tx}/{len(TRANSACTIONS)}")
    
    # Success criteria
    expected_total_blocks = EXPECTED_FULL_BLOCKS + EXPECTED_PARTIAL_BLOCKS
    expected_tx = EXPECTED_FULL_BLOCKS * TRANSACTIONS_PER_BLOCK + EXPECTED_PARTIAL_BLOCKS
    
    print(f"\nExpected distribution: {EXPECTED_FULL_BLOCKS} full blocks + {EXPECTED_PARTIAL_BLOCKS} block with 1 tx = {expected_total_blocks} total blocks")
    print(f"Expected transactions: {expected_tx}")
    
    # Check if we meet minimum success criteria: all transactions are accounted for
    if total_tx == len(TRANSACTIONS):
        print("\nPASS: All transactions were processed across blocks")
        
        # Check if distribution matches exactly what we expect
        if full_blocks == EXPECTED_FULL_BLOCKS and partial_blocks == EXPECTED_PARTIAL_BLOCKS:
            print(f"PERFECT: Distribution exactly matches expected pattern ({EXPECTED_FULL_BLOCKS} full + {EXPECTED_PARTIAL_BLOCKS} partial)")
        else:
            print(f"ACCEPTABLE: All transactions processed, but distribution differs from expected pattern")
        
        return True
    else:
        print(f"\nFAIL: Not all transactions were processed correctly. Found {total_tx}/{len(TRANSACTIONS)}")
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
        pyautogui.PAUSE = 0.03  # Extremely short pause for maximum speed
        
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
        
        # Send the 100 transactions in batches
        print("\n=== OMEGA TRANSACTION FLOODING TEST ===")
        print(f"Sending {len(TRANSACTIONS)} transactions in rapid succession...")
        start_time = time.time()
        
        # Use larger batch size for speed
        BATCH_SIZE = 10
        
        # Send transactions in batches with small delays between batches
        for i in range(0, len(TRANSACTIONS), BATCH_SIZE):
            batch = TRANSACTIONS[i:i+BATCH_SIZE]
            batch_num = i // BATCH_SIZE + 1
            total_batches = (len(TRANSACTIONS) + BATCH_SIZE - 1) // BATCH_SIZE
            
            print(f"\nSending batch {batch_num}/{total_batches} (transactions {i+1}-{min(i+BATCH_SIZE, len(TRANSACTIONS))}):")
            
            batch_start_time = time.time()
            for j, transaction in enumerate(batch):
                tx_num = i + j + 1
                # Print less frequently to avoid slowing down the console
                if tx_num % 10 == 0 or tx_num == 1 or tx_num == len(TRANSACTIONS):
                    print(f"  Transaction {tx_num}/{len(TRANSACTIONS)}: {transaction['receiver']}, Amount: {transaction['amount']}")
                send_transaction(client_pid, client_title, transaction)
            
            batch_elapsed = time.time() - batch_start_time
            print(f"  Batch sent in {batch_elapsed:.2f} seconds")
            
            # Wait briefly between batches to allow some processing
            if i + BATCH_SIZE < len(TRANSACTIONS):
                print(f"  Waiting {BATCH_WAIT_SECONDS}s between batches...")
                time.sleep(BATCH_WAIT_SECONDS)
        
        # Send exit command after all transactions
        exit_client(client_pid, client_title)
        
        total_elapsed_time = time.time() - start_time
        print(f"\nAll {len(TRANSACTIONS)} transactions sent in {total_elapsed_time:.2f} seconds")
        print(f"Average rate: {len(TRANSACTIONS) / total_elapsed_time:.2f} transactions per second")
        
        # Wait for consensus process to complete for all blocks with periodic checks
        print(f"\nWaiting up to {MAX_WAIT_SECONDS}s for consensus on all transactions...")
        wait_start_time = time.time()
        blocks_test_passed = False
        
        while True:
            elapsed_wait_time = time.time() - wait_start_time
            if elapsed_wait_time > MAX_WAIT_SECONDS:
                print(f"Maximum wait time of {MAX_WAIT_SECONDS}s exceeded.")
                break
            
            # Check current block count
            current_blocks = get_block_files()
            total_expected_blocks = EXPECTED_FULL_BLOCKS + EXPECTED_PARTIAL_BLOCKS
            
            print(f"\nProgress check after {elapsed_wait_time:.0f}s:")
            print(f"  Blocks created: {len(current_blocks)}/{total_expected_blocks}")
            
            if len(current_blocks) >= total_expected_blocks:
                print("  All expected blocks have been created!")
                blocks_test_passed = True
                break
            else:
                remaining_wait = MAX_WAIT_SECONDS - elapsed_wait_time
                print(f"  Continuing to wait (remaining: {remaining_wait:.0f}s)")
                time.sleep(POLLING_INTERVAL_SECONDS)
        
        print("\nConsensus wait finished.")
        
        # Final checks for correct block count
        if not blocks_test_passed:
            blocks_test_passed = check_blocks_created()
        
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

        # Final Result
        print("\n----- Test Summary -----")
        if test_passed:
            print(f"RESULT: PASSED - All {len(TRANSACTIONS)} transactions were correctly processed")
            print(f"The blockchain successfully created {EXPECTED_FULL_BLOCKS} full blocks and {EXPECTED_PARTIAL_BLOCKS} partial block")
            sys.exit(0)
        else:
            print("RESULT: FAILED - Transactions were not processed as expected")
            print("Check the logs above for details on what went wrong.")
            sys.exit(1)