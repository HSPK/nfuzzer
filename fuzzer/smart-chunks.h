

#ifndef __SMART_CHUNKS_H
#define __SMART_CHUNKS_H 1

struct chunk {
  unsigned long
      id; 
  int type;               
  int start_byte;          
  int end_byte;            
  char modifiable;         
  struct chunk *next;      
  struct chunk *children;  
};

extern void get_chunks(char *filespec, struct chunk **data_chunks);

extern void print_tree(struct chunk *root);

extern void print_tree_with_data(struct chunk *root, const char *data);

/* Note that for this to work smart_log_init() must have been called. */
extern void smart_log_tree(struct chunk *root);

/* Note that for this to work smart_log_init() must have been called. */
extern void smart_log_tree_with_data(struct chunk *root, const char *data);

extern void delete_chunks(struct chunk *node);

extern struct chunk *copy_chunks(struct chunk *node);

extern void increase_byte_positions_except_target_children(struct chunk *c,
                                                           struct chunk *target,
                                                           int start_byte,
                                                           unsigned size);

extern void reduce_byte_positions(struct chunk *c, int start_byte,
                                  unsigned size);

extern struct chunk *search_and_destroy_chunk(struct chunk *c,
                                              struct chunk *target_chunk,
                                              int start_byte, unsigned size);

/* Note that for this to work smart_log_init() must have been called. */
extern void smart_log_tree_hex(struct chunk *root);

/* Note that for this to work smart_log_init() must have been called. */
extern void smart_log_tree_with_data_hex(struct chunk *root, const char *data);

#endif /* __SMART_CHUNKS_H */
