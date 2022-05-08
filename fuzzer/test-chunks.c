

#include <stdio.h>

#include "smart-chunks.h"

int main(int argc, char **argv) {
  struct chunk *data_chunks = NULL;

  get_chunks("test-data/chunks/image.chunks", &data_chunks);  

  printf("IMAGE CHUNKS:\n");
  print_tree(data_chunks);

  delete_chunks(data_chunks);

  return 0;
}


