#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <netdb.h>
#include <sys/socket.h>
#include <netinet/in.h>

#include <sys/types.h> 
#include <sys/ipc.h> 
#include <sys/shm.h> 
#include <sys/wait.h>

#define FILE_READ_CHUNK 1024
#define DATA_SIZE 8192
#define SHM_SIZE 65536

// 将共享内存切分发送
#define SOCKET_READ_CHUNK 1024

#define SHM_ENV_VAR "__AFL_SHM_ID"

#define LOGFILE "/tmp/afl-wrapper.log"

#define VERBOSE 

#define STATUS_SUCCESS 0
#define STATUS_TIMEOUT 1
#define STATUS_CRASH 2
#define STATUS_QUEUE_FULL 3
#define STATUS_COMM_ERROR 4
#define STATUS_DONE 5

#define MAX_TRIES 40

#define DEFAULT_SERVER "localhost"
#define DEFAULT_PORT "7007"

#define DEFAULT_MODE 0
#define LOCAL_MODE 1

uint8_t* trace_bits;
int prev_location = 0;

// 将输出写入文件，因为运行AFL时没有控制台输出
FILE* logfile;

// 被AFL运行或自己运行
uint8_t in_afl = 0;

// 选择控制台输出还是文件输出
//#define OUTPUT_STDOUT
#define OUTPUT_FILE

void LOG(const char* format, ...) {
  va_list args;
#ifdef OUTPUT_STDOUT
    va_start(args, format);
    vprintf(format, args);
#endif
#ifdef OUTPUT_FILE
    va_start(args, format);
    vfprintf(logfile, format, args);
#endif
  va_end(args);
}

#define DIE(...) { LOG(__VA_ARGS__); if(!in_afl) shmdt(trace_bits); if(logfile != NULL) fclose(logfile); exit(1); }
#define LOG_AND_CLOSE(...) { LOG(__VA_ARGS__); if(logfile != NULL) fclose(logfile); }

#ifdef VERBOSE
  #define LOGIFVERBOSE(...) LOG(__VA_ARGS__);
#else
  #define LOGIFVERBOSE(...) 
#endif

int tcp_socket;

// 建立TCP连接
void setup_tcp_connection(const char* hostname, const char* port) {
  LOG("Trying to connect to server %s at port %s...\n", hostname, port);
  struct addrinfo hints;
  memset(&hints, 0, sizeof(hints));
  hints.ai_family = AF_UNSPEC;
  hints.ai_socktype = SOCK_STREAM;
  hints.ai_protocol = 0;
  hints.ai_flags = AI_ADDRCONFIG;
  struct addrinfo* res = 0;
  int err = getaddrinfo(hostname, port, &hints, &res);
  if (err!=0) {
    DIE("failed to resolve remote socket address (err=%d)\n", err);
  }

  tcp_socket = socket(res->ai_family, res->ai_socktype, res->ai_protocol);
  if (tcp_socket == -1) {
    DIE("%s\n", strerror(errno));
  }

  if (connect(tcp_socket, res->ai_addr, res->ai_addrlen) == -1) {
    DIE("%s\n", strerror(errno));
  }

  freeaddrinfo(res);
}

void printUsageAndDie() {
  DIE("Usage: interface [-s <server>] [-p <port>] <filename>\n");
}

int main(int argc, char** argv) {

// 文件输出
#ifdef OUTPUT_FILE
  logfile = fopen(LOGFILE, "wb");
  if (logfile == NULL) {
    DIE("Error opening log file for writing\n");
  }
#endif

  const char* filename;
  char* server = DEFAULT_SERVER;
  char* port = DEFAULT_PORT;

  // 至少需要 interface @@ 两个参数
  if (argc < 2)
    printUsageAndDie();

  // 解析命令行参数
  int curArg = 1;
  while (curArg < argc) {
    if (argv[curArg][0] == '-') { //flag
      if (argv[curArg][1] == 's') {
    // 设置server
	server = argv[curArg+1];
	curArg += 2;
      } else if (argv[curArg][1] == 'p') {
    // 设置port
	port = argv[curArg+1];
	curArg += 2;
      } else {
        LOG("Unknown flag: %s\n", argv[curArg]);
	printUsageAndDie();
      }
    } else {
      break;
    }
  }
  // 存在未知参数
  if (curArg != argc-1)
    printUsageAndDie();

  filename = argv[curArg];
  LOG("input file = %s\n", filename);

  // 若没有设置server，则为local_mode
  uint8_t mode = DEFAULT_MODE;
  if (strcmp(server, "localhost") == 0) {
    LOG("Running in LOCAL MODE.\n");
    mode = LOCAL_MODE;
  }

  // 开始插桩
  char* shmname = getenv(SHM_ENV_VAR);
  int status = 0;
  uint8_t nfuzzer_status = STATUS_SUCCESS;
  if (shmname) {

    // 运行于AFL中
    in_afl = 1;
	  
    // 建立共享内存区域
    LOG("SHM_ID: %s\n", shmname);
    key_t key = atoi(shmname);

    if ((trace_bits = shmat(key, 0, 0)) == (uint8_t*) -1) {
      DIE("Failed to access shared memory 2\n");
    }
    LOGIFVERBOSE("Pointer: %p\n", trace_bits);
    LOG("Shared memory attached. Value at loc 3 = %d\n", trace_bits[3]);

    // 建立 fork server
    LOG("Starting fork server...\n");
    if (write(199, &status, 4) != 4) {
      LOG("Write failed\n");
      goto resume;
    }

    while(1) {
      if(read(198, &status, 4) != 4) {
         DIE("Read failed\n");
      }

      int child_pid = fork();
      if (child_pid < 0) {
        DIE("Fork failed\n");
      } else if (child_pid == 0) {
        LOGIFVERBOSE("Child process, continue after pork server loop\n");
	break;
      }

      LOGIFVERBOSE("Child PID: %d\n", child_pid);
      write(199, &child_pid, 4);
      
      LOGIFVERBOSE("Status %d \n", status);

      if(waitpid(child_pid, &status, 0) <= 0) {
        DIE("Fork crash");
      }

      LOGIFVERBOSE("Status %d \n", status);
      write(199, &status, 4);
    }

    resume:
    LOGIFVERBOSE("AFTER LOOP\n\n");
    close(198);
    close(199);

    // 向AFL证明程序已被插桩
    trace_bits[0]++;

  } else {
    LOG("Not running within AFL. Shared memory and fork server not set up.\n");
    trace_bits = (uint8_t*) malloc(SHM_SIZE);
  }

  // 完成准备工作，开始测试
  int try = 0;
  size_t nread;
  char buf[FILE_READ_CHUNK];
  FILE *file;
  uint8_t conf = STATUS_DONE;

  // 在 MAX_TRIES 时间内尝试与server通信
  do {
    // 如果不是第一次连接，sleep 0.1s
    if(try > 0)
      usleep(100000);

    setup_tcp_connection(server, port);

    // 将 mode 信息发送到 nfuzzer 端
    write(tcp_socket, &mode, 1);

    // local_mode
    if (mode == LOCAL_MODE) {

      // 获取绝对地址
      char path[10000];
      realpath(filename, path);

      // 发送地址长度
      int pathlen = strlen(path);
      if (write(tcp_socket, &pathlen, 4) != 4) {
        DIE("Error sending path length");
      }
      LOG("Sent path length: %d\n", pathlen);

      // 发送地址
      if (write(tcp_socket, path, pathlen) != pathlen) {
        DIE("Error sending path");
      }
      LOG("Sent path: %s\n", path);

    
    // default_mode
    } else {

      // 直接发送文件内容
      file = fopen(filename, "r");
      if (file) {

        // 获取文件大小并发送
        fseek(file, 0L, SEEK_END);
        int filesize = ftell(file);
        rewind(file);
        LOG("Sending file size %d\n", filesize);
        if (write(tcp_socket, &filesize, 4) != 4) {
          DIE("Error sending filesize");
        }

        // 发送文件的字节流
        size_t total_sent = 0;
        while ((nread = fread(buf, 1, sizeof buf, file)) > 0) {
          if (ferror(file)) {
            DIE("Error reading from file\n");
          }
          ssize_t sent = write(tcp_socket, buf, nread);
          total_sent += sent;
          LOG("Sent %lu bytes of %lu\n", total_sent, filesize);
        }
        fclose(file);
      } else {
        DIE("Error reading file %s\n", filename);
      }
    }

    // 通过TCP通信获取 nfuzzer_status (测试结果)
    nread = read(tcp_socket, &nfuzzer_status, 1);
    if (nread != 1) {
      LOG("Failure reading exit status over socket.\n");
      nfuzzer_status = STATUS_COMM_ERROR;
      goto cont;
    }
    LOG("Return nfuzzer_status = %d\n", status);
  
    // 获取共享内存 (边缘覆盖)
    uint8_t *shared_mem = malloc(DATA_SIZE);
    for (int offset = 0; offset < DATA_SIZE; offset += SOCKET_READ_CHUNK) {
      nread = read(tcp_socket, shared_mem+offset, SOCKET_READ_CHUNK);
      if (nread != SOCKET_READ_CHUNK) {
	LOG("Error reading from socket\n");
	nfuzzer_status = STATUS_COMM_ERROR;
	goto cont;
      }
    }

    // 将获得的覆盖信息写入真正的共享内存区域
    for (int i = 0; i < DATA_SIZE; i++) {
      if (shared_mem[i] != 0) {
        uint8_t temp = shared_mem[i];
        for (int j = 0; j < 8; j++) {
          if ((temp & 1) == 1) {
            trace_bits[i * 8 + j] += 1; 
            LOG("%d -> %d\n", i * 8 + j, trace_bits[i * 8 + j]);
          }
          temp = temp >> 1;
        }
        //LOG("%d -> %d\n", i, shared_mem[i]);
        //trace_bits[i] += shared_mem[i];
      }
    }

    // 关闭 socket
    cont: close(tcp_socket);

    // 只尝试 MAX_TRIES 时间
    if (try++ > MAX_TRIES) {
      // 失败
      DIE("Stopped trying to communicate with server.\n");
    }

  } while (nfuzzer_status == STATUS_QUEUE_FULL || nfuzzer_status == STATUS_COMM_ERROR);
    
  LOG("Received results. Terminating.\n\n");

  // 断开共享内存连接
  if (in_afl) {
    shmdt(trace_bits);
  }

  // 如果 java 程序异常终止，则使用 abort() 函数终止本程序
  if (nfuzzer_status == STATUS_CRASH) {
    LOG("Crashing...\n");
    abort();
  }

  // 若 java 端超时，就一直循环等待直到 AFL 端判断本次测试超时
  if (nfuzzer_status == STATUS_TIMEOUT) {
    LOG("Starting infinite loop...\n");
    while (1) {
      sleep(10);
    }
  }

  LOG_AND_CLOSE("Terminating normally.\n");

  return 0;
}

