#include "sc.h"
#include <stdlib.h>

list_node *pc_list_add(list_node **head, void *data) {
    list_node *node = (list_node *)calloc(1, sizeof(list_node));
    if (node == NULL)
        return NULL;
    node->data = data;
    node->next = *head;
    *head = node;
    return node;
}

void pc_list_free(list_node *head) {
    while (head != NULL) {
        list_node *next = head->next;
        free(head);
        head = next;
    }
}

int pc_list_count(list_node *head) {
    int count = 0;
    while (head != NULL) {
        count++;
        head = head->next;
    }
    return count;
}
