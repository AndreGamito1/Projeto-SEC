import subprocess
import time
import json
import sys
import os
import shutil
import signal
import io # For capturing output

# --- Configuration ---
PROJECT_ROOT = os.getcwd()
PRIV_KEYS_DIR = os.path.join(PROJECT_ROOT, "src/main/resources/priv_keys")
PUB_KEYS_DIR = os.path.join(PROJECT_ROOT, "src/main/resources/pub_keys")
LATEST_BLOCK_FILE = os.path.join(PROJECT_ROOT, "src/main/resources/blocks/block1.json")

MEMBERS = ["member1", "member2", "member3", "member4"]
MEMBER_MAIN_CLASS = "com.depchain.consensus.Main"
CLIENT_MAIN_CLASS = "com.depchain.client.Client"
CLIENT_LIB_MAIN_CLASS = "com.depchain.client.ClientLibrary"

CLIENT1_ARGS = ["Pereira"]
# Ensure these ports are correct for ClientLibrary constructor (clientPort, httpPort)
CLIENT_LIB_ARGS = ["8080", "8081"]

MAVEN_COMMAND = "mvn"

# --- Define Sequence of Client Interactions ---
# List of actions for client1 (Pereira) to perform
# Supported actions: 'transaction', 'balance'
interactions = [
    {"action": "transaction", "receiver": "Miguel", "amount": "20"},
    # Add a second transaction
    {"action": "transaction", "receiver": "Gamito", "amount": "15"}, # Assuming Sofia is another client/address
    # Add a balance check (optional - note: output isn't checked mid-script)
    {"action": "balance"},
    # Add more transactions or balance checks as needed
]

# --- Expected Final State ---
# *** CRITICAL: Update these balances based on ALL interactions above! ***
# Example: If Pereira starts with 100, sends 20 to Miguel, sends 15 to Sofia.
# Pereira should end with 100 - 20 - 15 = 65.
# Miguel should end with initial_miguel + 20.
# Sofia should end with initial_sofia + 15.
# You MUST know the initial state and map names to addresses.
EXPECTED_BALANCES = {
    # Example - Replace with your actual addresses and expected final balances
    "0xFEDCBA9876543210FEDCBA9876543210FEDCBA98": 65.0,  # Pereira's final balance?
    "0x1234567890ABCDEF1234567890ABCDEF12345678": 100.0, # 
    "0xA1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9B0": 120.0, # Miguel's final balance? (initial + 20)
    "0x11223344556677889900AABBCCDDEEFF11223344": 115.0, # Gamito
    "0x5678ABCDEF1234567890ABCDEF1234567890ABCD": 100.0, # Unchanged address
}

# --- Timing ---
MEMBER_START_DELAY = 1
CLIENT_LIB_STARTUP_DELAY = 3
CLIENT_INTERACTION_DELAY = 0.7 # Slightly longer delay between inputs might be safer
# Increase consensus wait if multiple rounds might be needed
CONSENSUS_WAIT_SECONDS = 45 # Increased from 30, adjust as needed
CHECKING_POLL_INTERVAL_SECONDS = 5
CHECKING_MAX_WAIT_SECONDS = 90

# --- Helper Functions ---
# (clean_directories, start_process, check_balances, terminate_processes
#  remain the same as the previous working version with output capturing)
def clean_directories():
    print("Cleaning key directories...")
    if os.path.exists(LATEST_BLOCK_FILE):
        try: os.remove(LATEST_BLOCK_FILE); print(f"  Removed: {LATEST_BLOCK_FILE}")
        except OSError as e: print(f"  Warning: Could not remove {LATEST_BLOCK_FILE}: {e}")
    for dir_path in [PRIV_KEYS_DIR, PUB_KEYS_DIR]:
        if os.path.exists(dir_path):
            try: shutil.rmtree(dir_path); print(f"  Removed: {dir_path}")
            except OSError as e: print(f"  Error removing {dir_path}: {e}")
        try: os.makedirs(dir_path, exist_ok=True); print(f"  Created: {dir_path}")
        except OSError as e: print(f"  Error creating {dir_path}: {e}"); sys.exit(1)

def start_process(args, title, enable_stdin=False, capture_output=False):
    stdin_pipe = subprocess.PIPE if enable_stdin else None
    stdout_pipe = subprocess.PIPE if capture_output else None
    stderr_pipe = subprocess.PIPE if capture_output else None
    try:
        print(f"Starting: {title} ({' '.join(args)})...")
        process = subprocess.Popen(
            args, cwd=PROJECT_ROOT, stdin=stdin_pipe, stdout=stdout_pipe, stderr=stderr_pipe,
            text=True, encoding='utf-8', bufsize=1
        )
        print(f"  Started {title} with PID: {process.pid}")
        return process
    except FileNotFoundError: print(f"Error: Command '{args[0]}' not found."); sys.exit(1)
    except Exception as e: print(f"Error starting {title}: {e}"); sys.exit(1)

def check_balances(block_file):
    print(f"Checking balances in {block_file}...")
    try:
        with open(block_file, 'r') as f: data = json.load(f)
        if "state" not in data or not isinstance(data["state"], dict):
            print("  Error: 'state' field missing or not a dictionary in JSON."); return False
        actual_state = data["state"]
        mismatches = []; all_ok = True
        for address, expected_balance_float in EXPECTED_BALANCES.items():
            if address not in actual_state:
                mismatches.append(f"Missing address {address}"); all_ok = False; continue
            actual_balance_data = actual_state[address]
            if not isinstance(actual_balance_data, dict) or "balance" not in actual_balance_data:
                 mismatches.append(f"Invalid data format for {address}: Got {actual_balance_data}"); all_ok = False; continue
            try:
                actual_balance_str = actual_state[address].get("balance", "N/A")
                actual_balance_float = float(actual_balance_str)
            except (ValueError, TypeError) as e:
                 mismatches.append(f"Invalid balance format for {address}: '{actual_balance_str}' ({e})"); all_ok = False; continue
            expected_is_float = isinstance(expected_balance_float, float)
            tolerance = 0.001 if expected_is_float else 0
            if not abs(actual_balance_float - expected_balance_float) <= tolerance:
                 expected_fmt = f"{expected_balance_float:.2f}" if expected_is_float else str(int(expected_balance_float))
                 actual_fmt = f"{actual_balance_float:.2f}" if expected_is_float else str(int(actual_balance_float))
                 mismatches.append(f"Address {address}: Expected {expected_fmt}, Got {actual_fmt}"); all_ok = False
        if all_ok: print("  Balance check PASSED!"); return True
        else:
            print("  Balance check FAILED:");
            for mismatch in mismatches: print(f"    - {mismatch}");
            return False
    except json.JSONDecodeError: print(f"  Waiting: {block_file} not valid JSON yet."); return False
    except FileNotFoundError: print(f"  Waiting: {block_file} does not exist yet."); return False
    except Exception as e: print(f"  Error reading/processing {block_file}: {e}"); return False

def terminate_processes(process_list_with_output):
    print("\nTerminating background processes...")
    for title, proc, captured_stdout, captured_stderr in list(process_list_with_output):
        stdout_data = ""; stderr_data = ""
        capture_output_flag = captured_stdout is not None # Check if we intended to capture
        if proc and capture_output_flag:
            try:
                if proc.stdout:
                    os.set_blocking(proc.stdout.fileno(), False)
                    stdout_data = captured_stdout.getvalue() + proc.stdout.read()
                if proc.stderr:
                    os.set_blocking(proc.stderr.fileno(), False)
                    stderr_data = captured_stderr.getvalue() + proc.stderr.read()
            except Exception as e: print(f"    Warning: Error reading final output from {title}: {e}")
        if proc and proc.poll() is None:
            print(f"  Terminating {title} (PID: {proc.pid})...")
            try:
                proc.terminate(); proc.wait(timeout=5)
                print(f"    {title} (PID {proc.pid}) terminated.")
            except subprocess.TimeoutExpired:
                print(f"    {title} (PID {proc.pid}) killing..."); proc.kill(); proc.wait()
                print(f"    {title} (PID {proc.pid}) killed.")
            except Exception as e: print(f"    Error terminating {title} (PID {proc.pid}): {e}")
        elif proc: print(f"  {title} (PID {proc.pid}) already finished (exit code {proc.poll()}).")
        if stdout_data: print(f"--- Captured STDOUT for {title} ---\n{stdout_data.strip()}\n--- End STDOUT ---")
        if stderr_data: print(f"--- Captured STDERR for {title} ---\n{stderr_data.strip()}\n--- End STDERR ---")

# --- Main Execution ---
if __name__ == "__main__":
    clean_directories()

    processes_info = []
    client1_proc = None
    client_lib_proc = None
    test_passed = False
    capture_output = True # Keep capturing output for debugging

    try:
        # Start Members
        print("\nStarting members...")
        for member in MEMBERS:
            cmd = [MAVEN_COMMAND, "exec:java", f"-Dexec.mainClass={MEMBER_MAIN_CLASS}", f"-Dexec.args={member}"]
            proc = start_process(cmd, member, capture_output=capture_output)
            stdout_buf = io.StringIO() if capture_output else None
            stderr_buf = io.StringIO() if capture_output else None
            if proc: processes_info.append((member, proc, stdout_buf, stderr_buf))
            time.sleep(MEMBER_START_DELAY)

        # Start Client Library
        print("\nStarting client library...")
        client_lib_cmd = [MAVEN_COMMAND, "exec:java", f"-Dexec.mainClass={CLIENT_LIB_MAIN_CLASS}", f"-Dexec.args={' '.join(CLIENT_LIB_ARGS)}"]
        client_lib_proc = start_process(client_lib_cmd, "ClientLibrary", capture_output=capture_output)
        client_lib_stdout = io.StringIO() if capture_output else None
        client_lib_stderr = io.StringIO() if capture_output else None
        if client_lib_proc: processes_info.append(("ClientLibrary", client_lib_proc, client_lib_stdout, client_lib_stderr))
        else: raise Exception("Failed to start ClientLibrary")
        print(f"Waiting {CLIENT_LIB_STARTUP_DELAY}s for ClientLibrary...")
        time.sleep(CLIENT_LIB_STARTUP_DELAY)

        # Start Client1
        print("\nStarting client1 (Pereira)...")
        client1_cmd = [MAVEN_COMMAND, "exec:java", f"-Dexec.mainClass={CLIENT_MAIN_CLASS}", f"-Dexec.args={' '.join(CLIENT1_ARGS)}"]
        client1_proc = start_process(client1_cmd, "client1", enable_stdin=True, capture_output=capture_output)
        client1_stdout = io.StringIO() if capture_output else None
        client1_stderr = io.StringIO() if capture_output else None
        if client1_proc: processes_info.append(("client1", client1_proc, client1_stdout, client1_stderr))
        else: raise Exception("Failed to start client1 process")
        time.sleep(2) # Wait for client prompt

        # --- Send Multiple Interactions to Client1 ---
        if client1_proc and client1_proc.stdin:
            print("\nSending commands to client1...")
            interaction_num = 1
            for interaction in interactions:
                action = interaction.get("action")
                print(f"-- Interaction {interaction_num}: {action} --")
                try:
                    if action == "transaction":
                        receiver = interaction.get("receiver")
                        amount = interaction.get("amount")
                        if not receiver or not amount:
                            print(f"  Skipping invalid transaction: {interaction}")
                            continue

                        print("  Sending: 1 (Append Data)")
                        client1_proc.stdin.write("1\n")
                        client1_proc.stdin.flush()
                        time.sleep(CLIENT_INTERACTION_DELAY)

                        print(f"  Sending Receiver: {receiver}")
                        client1_proc.stdin.write(f"{receiver}\n")
                        client1_proc.stdin.flush()
                        time.sleep(CLIENT_INTERACTION_DELAY)

                        print(f"  Sending Amount: {amount}")
                        client1_proc.stdin.write(f"{amount}\n")
                        client1_proc.stdin.flush()
                        print(f"  Transaction details sent ({receiver}, {amount}).")
                        time.sleep(CLIENT_INTERACTION_DELAY) # Pause after sending

                    elif action == "balance":
                        print("  Sending: 2 (Check Balance)")
                        client1_proc.stdin.write("2\n")
                        client1_proc.stdin.flush()
                        print("  Balance check command sent.")
                        # NOTE: We are NOT reading the output here. The script
                        # relies on the final block state check later.
                        time.sleep(CLIENT_INTERACTION_DELAY) # Pause after sending

                    else:
                        print(f"  Skipping unknown action: {action}")

                except (BrokenPipeError, OSError) as e:
                    print(f"  Error writing to client1 stdin during interaction {interaction_num}: {e}. Aborting interactions.")
                    break # Stop sending commands if pipe breaks
                except Exception as e:
                    print(f"  Unexpected error during interaction {interaction_num}: {e}")
                    raise # Rethrow other unexpected errors
                interaction_num += 1

            # --- Optionally send Exit command ---
            try:
                print("\nSending: 0 (Exit)")
                client1_proc.stdin.write("0\n")
                client1_proc.stdin.flush()
                print("Closing client1 stdin.")
                client1_proc.stdin.close() # Signal end of input
            except (BrokenPipeError, OSError) as e:
                 print(f"  Warning: Could not send exit command or close stdin: {e}")

        else:
            print("Error: client1 process or its stdin not available.")
            raise Exception("Client1 interaction failed")

        # --- Explicit Wait for Consensus ---
        print(f"\nWaiting {CONSENSUS_WAIT_SECONDS} seconds for consensus after interactions...")
        time.sleep(CONSENSUS_WAIT_SECONDS)
        print("Consensus wait finished.")

        # --- Monitoring Phase (Check block file) ---
        print(f"\nNow checking for {LATEST_BLOCK_FILE} and verifying final balances...")
        print(f"Polling interval: {CHECKING_POLL_INTERVAL_SECONDS}s, Max wait: {CHECKING_MAX_WAIT_SECONDS}s")
        checking_start_time = time.time()
        while True:
            elapsed_checking_time = time.time() - checking_start_time
            if elapsed_checking_time > CHECKING_MAX_WAIT_SECONDS:
                print("\nTimeout reached while checking for block file and correct balances.")
                break
            lib_terminated = client_lib_proc and client_lib_proc.poll() is not None
            if lib_terminated:
                 print(f"\nERROR: ClientLibrary terminated unexpectedly (exit code {client_lib_proc.poll()}). Stopping check.")
                 break
            if os.path.exists(LATEST_BLOCK_FILE):
                 if check_balances(LATEST_BLOCK_FILE):
                     test_passed = True
                     print("Successful balance check achieved.")
                     break
            if not test_passed:
                 remaining_wait = CHECKING_MAX_WAIT_SECONDS - elapsed_checking_time
                 print(f"Checking... (Remaining check time: {int(remaining_wait)}s)")
            time.sleep(CHECKING_POLL_INTERVAL_SECONDS)

    except KeyboardInterrupt: print("\nTest interrupted by user.")
    except Exception as e:
        print(f"\nAn unexpected error occurred during test execution: {e}")
        import traceback; traceback.print_exc()
    finally:
        # --- Cleanup & Output ---
        terminate_processes(processes_info)

        # --- Final Result ---
        print("\n----- Test Summary -----")
        if test_passed:
            print("RESULT: PASSED"); sys.exit(0)
        else:
            print("RESULT: FAILED")
            # Add specific failure reasons based on observations
            if client_lib_proc and client_lib_proc.poll() is not None: print(f"Reason: ClientLibrary terminated unexpectedly (Exit Code: {client_lib_proc.poll()}). Check its logs.")
            elif not os.path.exists(LATEST_BLOCK_FILE) and time.time() - checking_start_time > CHECKING_MAX_WAIT_SECONDS: print(f"Reason: Timeout waiting for {LATEST_BLOCK_FILE}.")
            elif os.path.exists(LATEST_BLOCK_FILE): print("Reason: Balances in latest block did not match expected values.")
            else: print("Reason: Test failed, check logs above.")
            sys.exit(1)