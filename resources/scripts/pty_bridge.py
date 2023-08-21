import os
import pty
import shlex
import sys
import select
import threading
import signal
import termios

def main():
    def read(fd):
        buffer = b""
        while True:
            chunk = os.read(fd, 1)  # Read one byte at a time
            buffer += chunk
            try:
                # Attempt to decode buffer as UTF-8
                decoded_chunk = buffer.decode('utf-8')
                sys.stdout.write(decoded_chunk)
                sys.stdout.flush()
                buffer = b"" # Clear buffer after successful decoding
            except UnicodeDecodeError:
                # If decoding fails, keep accumulating bytes
                pass

            if not chunk:
                # Stop reading when no more data is available
                break

    def write(fd, data):
        os.write(fd, data.encode())

    # Open PTY
    master, slave = pty.openpty()

    # Get the current terminal attributes
    attrs = termios.tcgetattr(slave)

    # Clear the ECHO flag
    attrs[3] = attrs[3] & ~termios.ECHO

    # Set the new terminal attributes
    termios.tcsetattr(slave, termios.TCSANOW, attrs)

    # Fork a child process
    pid = os.fork()

    if pid == 0: # Child process
        os.close(master)
        os.dup2(slave, 0) # Redirect stdin
        os.dup2(slave, 1) # Redirect stdout
        command_line = sys.argv[1]
        command_parts = shlex.split(command_line)
        os.execvp(command_parts[0], command_parts) # Execute command

        os.close(slave)

    else: # Parent process
        os.close(slave)
        def read_thread():
            read(master)
            print("Child process has terminated, exiting.")
            os.close(master) # Close the master end of the PTY
            sys.exit(0)  # Exit the parent process

        def write_loop():
            while True:
                input_data = sys.stdin.readline()
                if input_data.strip() == '[[CTRL-C]]':
                    write(master, '\x03')
                    os.kill(pid, signal.SIGINT)
                else:
                    write(master, input_data)

        threading.Thread(target=read_thread, daemon=True).start()
        write_loop()  # Run write_loop in main thread

if __name__ == '__main__':
    main()
