### Demo of the SOW/Subscribe ordering failing in Apache Ignite continuous query

(where SOW = State Of World, the initial cache data)

- Each time the Main class runs, it adds an Ignite node to the cluster (where
  the 1st node establishes the local cluster... i.e. the demo is
  self-sufficient)
- 2nd node sets up a Continuous Query, the one where ordering fails later.
- 3rd node writes data to the cache - the data that the 2nd node listens to.
- (so don't add a 4th node...)
- The 3rd node fills in the cache first, then updates the data it just added
  multiple times, each time each update entity gets a new version.
- Once all data is written & updated by the 3rd node, and it shows the log line
  saying that, go to the 2nd node and check its log. Each WARN in the 2nd node
  represents a troubled case - that some entity's update or initial value came
  in out of order. 