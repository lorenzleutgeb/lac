module Main where

import InterpreterPrelude

-- This module is generated by build.sh
import Output


inorder :: Show a => Tree a -> String
inorder Nil = " _ "
inorder (Node l x r) = (inorder l) ++ (show x) ++ (inorder r)

check :: Eq a => a -> a -> String
check a b = show ((==) a b)

-- Constants

example_t1 = Node (bottom 2) 1 (bottom 3)
example_t2 = Node (bottom 5) 4 (bottom 6)
example_t3 = Node (Node (bottom 4) 2 (bottom 5)) 1 (bottom 3)

to5 = [1, 2, 3, 4, 5] :: [Integer]
to5r = make_list_right to5
to5l = make_list_left to5

main :: IO ()
main = mapM_ putStrLn [
  inorder example_t1,
  inorder example_t2,
  inorder (append_right example_t1 example_t2),
  inorder (append_left example_t1 example_t2),
  inorder (make_list_left to5),
  inorder (make_list_right to5),
  (show . read_left) (make_list_left to5),
  (show . read_right) (make_list_right to5),
  (show . read_left) (make_list_right to5),
  (show . read_right) (make_list_left to5),
  inorder (rev_append_right example_t1 example_t2),
  (show . read_right) (append_right to5r to5r),
  (show . read_right) (rev_append_right to5r to5r),
  "1 2 3 4 5 1 2 3 4 5:",
  (show . read_left) (append_left to5l to5l),
  show (reverse to5),
  check ((reverse to5) ++ to5)
  (read_left (rev_append_left to5l to5l)),
  check [1, 2, 3] (read_left (preorder_left example_t1 Nil)),
  check [1, 2, 3, 4, 5] (read_left (preorder_left example_t1 example_t2)),
  check [1, 2, 4, 5, 3] (read_left (preorder_left example_t3 Nil)),
  check [4, 5, 2, 3, 1] (read_left (postorder_left example_t3 Nil)),
  check [4, 2, 5, 1, 3] (read_left (inorder_left example_t3 Nil)),
  check Nil (pop_left (push_left Nil 1)),
  show (rank example_t3),
  show (rank to5l),
  show (rank to5r)]