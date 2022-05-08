#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <netdb.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>


#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>

 

/*
 连接到服务器后，会不停循环，等待输入，
 输入quit后，断开与服务器的连接
 */

int main(int argc, char** argv)
{
 int SERVER_PORT= atoi(argv[2]);
 printf("%s\n%d",argv[1],SERVER_PORT);
//客户端只需要一个套接字文件描述符，用于和服务器通信
 int clientSocket;
    //描述服务器的socket
 struct sockaddr_in serverAddr;
 char sendbuf[]="zzj";
 char recvbuf[200]={};



 if((clientSocket = socket(AF_INET, SOCK_STREAM, 0)) < 0)
 {
 perror("socket");
 return 1;
 }

 serverAddr.sin_family = AF_INET;
 serverAddr.sin_port = htons(SERVER_PORT);
    //指定服务器端的ip，本地测试：127.0.0.1
    //inet_addr()函数，将点分十进制IP转换成网络字节序IP
 serverAddr.sin_addr.s_addr = inet_addr(argv[1]);
 if(connect(clientSocket, (struct sockaddr *)&serverAddr, sizeof(serverAddr)) < 0)
 {
 perror("connect");
 return 1;
 }

 //printf("connect with destination host...\n");
 
 //printf("Input your world:>");
 //scanf("%s", sendbuf);
 //printf("\n");


 send(clientSocket, sendbuf, strlen(sendbuf), 0);
 printf("send ok\n");
 recv(clientSocket, recvbuf, 200, 0);
 recvbuf[13] = '\0';
 printf("%s\n", recvbuf);
 
 close(clientSocket);
 return 0;
}
