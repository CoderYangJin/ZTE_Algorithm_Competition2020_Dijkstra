import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class DijkstraZTE2020 {
    private static int nodeNum;//站点数
    private static int linkNum;//轨道数
    private static int carNum;//列车数
    private static double carCapacity;//列车容量
    private static int requestNum;//货物信息
    private static Node[] nodes;//保存站点信息
    private static Link[] links;//保存轨道信息
    private static ArrayList<Item> itemsWithoutIncNode;//无必经站点的货物及合
    private static ArrayList<Item> itemsWithIncNode;//有必经站点的货物及合
    private static double[][] matrix;//网络信息
    private static ArrayList<Link> linkRoute;

    public static void main(String[] args) {
        try {
            //读取数据
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

            String s = in.readLine();  //站点数，轨道数，列车数，列车容量
            String[] dataSize = s.split(",");
            nodeNum = Integer.parseInt(dataSize[0]);//站点数
            linkNum = Integer.parseInt(dataSize[1]);//轨道数
            carNum = Integer.parseInt(dataSize[2]);//列车数
            carCapacity = Double.parseDouble(dataSize[3]);//列车容量

            //货物站点轨道从0开始 列车号从1开始
            //保存站点信息
            nodes = new Node[nodeNum];
            for (int i = 0; i < nodeNum; i++) {
                String[] nodeStr = in.readLine().split(",");    //每个站点的拣货员数据
                int nodeId = Integer.parseInt(nodeStr[0].substring(1));
                ArrayList<Link> relatedLink = new ArrayList<>();
                int totalWorkerNum = Integer.parseInt(nodeStr[1]);
                Node node = new Node(nodeId, relatedLink, totalWorkerNum, totalWorkerNum);
                nodes[nodeId] = node;
            }
            //保存轨道信息
            links = new Link[linkNum];
            Double cost = 1.0;
            for (int i = 0; i < linkNum; i++) {
                String[] linkStr = in.readLine().split(",");     //每条轨道的起止点
                int linkId = Integer.parseInt(linkStr[0].substring(1));
                Node srcNode = nodes[Integer.parseInt(linkStr[1].substring(1))];
                Node dstNode = nodes[Integer.parseInt(linkStr[2].substring(1))];
                //列车号从1开始 Link上的车
                ArrayList<Car> linkCars = new ArrayList<>();
                for (int j = 0; j < carNum; j++) {
                    Car car = new Car(linkId, j + 1, carCapacity, carCapacity);
                    linkCars.add(car);
                }
                Link link = new Link(linkId, srcNode, dstNode, linkCars, cost, carNum * carCapacity, carNum * carCapacity);
                links[linkId] = link;
                //维护node的relatedLink 与Node相连的路 用于找最短路径时根据节点找到link
                link.srcNode.relatedLink.add(link);
                link.dstNode.relatedLink.add(link);
            }
            //货物信息
            requestNum = Integer.parseInt(in.readLine());//货物数量
            itemsWithoutIncNode = new ArrayList<>();
            itemsWithIncNode = new ArrayList<>();
            for (int i = 0; i < requestNum; i++) {
                String[] requestStr = in.readLine().split(",");    //每个货物相关信息
                int itemId = Integer.parseInt(requestStr[0].substring(1));
                //id小的放src 大的放dst 用于处理首尾节点相同 但方向相反节点的合并
                int a = Integer.parseInt(requestStr[1].substring(1));
                int b = Integer.parseInt(requestStr[2].substring(1));
                Node srcNode = nodes[a < b ? a : b];
                Node dstNode = nodes[a >= b ? a : b];
                double itemWeight = Double.parseDouble(requestStr[3]);
                ArrayList<Node> incNode = new ArrayList<>();
                for (int j = 4; j < requestStr.length; j++) {
                    if (!requestStr[j].equals("null")) {
                        incNode.add(nodes[Integer.parseInt(requestStr[j].substring(1))]);
                    }
                }
                Item item = new Item(itemId, srcNode, dstNode, itemWeight, incNode);
                if (incNode.size() == 0) {
                    itemsWithoutIncNode.add(item);
                } else {
                    itemsWithIncNode.add(item);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        //组合货物集合
        ArrayList<CombileItem> combileItems = new ArrayList<>();
        //处理itemsWithoutIncNode 整合后存入combileItems
        addItems(combileItems, itemsWithoutIncNode);
        //处理itemsWithIncNode 整合后存入combileItems
        addItems(combileItems, itemsWithIncNode);

        //根据货物重量依次获得最短路径并更新
        //根据重量降序排列
        Collections.sort(combileItems, new Comparator<CombileItem>() {
            @Override
            public int compare(CombileItem o1, CombileItem o2) {
                return (int) (o2.itemTotalWeight - o1.itemTotalWeight);
            }
        });
        //获取路径
        //初始化
        matrix = new double[nodeNum][nodeNum];
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[0].length; j++) {
                matrix[i][j] = Double.MAX_VALUE / 2 - 1;
            }
        }
        for (int i = 0; i < linkNum; i++) {
            Link link = links[i];
            int startId = link.srcNode.nodeId;
            int endId = link.dstNode.nodeId;
            matrix[startId][endId] = link.linkCost;
            matrix[endId][startId] = link.linkCost;
        }
        //遍历寻优
        for (CombileItem combileItem : combileItems) {
            //判断起点终点有无拣货员
            if (combileItem.srcNode.availWorkerNum <= 0 || combileItem.dstNode.availWorkerNum <= 0) {
                combileItem.linkRoute = null;
                combileItem.cars = null;
                continue;
            }
            double[][] clone = new double[matrix.length][matrix.length];
            for (int i = 0; i < matrix.length; i++) {
                for (int j = 0; j < matrix.length; j++) {
                    clone[i][j] = matrix[i][j];
                }
            }
            //规划路径并保存
            //有无必经节点有些不同
            if (combileItem.incNode.size() > 0) {
                dijkstraWithIncNode(clone, combileItem);
            } else {
                dijkstraWithoutIncNode(clone, combileItem);
            }
        }
        //失败再处理
        for (CombileItem combileItem : combileItems) {
            //首尾站点有无拣货员
            if (combileItem.linkRoute == null
                    && combileItem.srcNode.availWorkerNum > 0
                    && combileItem.dstNode.availWorkerNum > 0) {
                double[][] clone = new double[matrix.length][matrix.length];
                for (int i = 0; i < matrix.length; i++) {
                    for (int j = 0; j < matrix.length; j++) {
                        clone[i][j] = matrix[i][j];
                    }
                }
                //有无必经节点有些不同
                int cnt = 0;
                if (combileItem.incNode.size() > 0) {
                    dijkstraWithIncNode(clone, combileItem);
                } else {
                    dijkstraWithoutIncNode(clone, combileItem);
                }
                while (combileItem.linkRoute == null && cnt++ < 4) {
                    for (Link link : linkRoute) {
                        clone[link.srcNode.nodeId][link.dstNode.nodeId] = Double.MAX_VALUE / 2 - 1;
                        clone[link.dstNode.nodeId][link.srcNode.nodeId] = Double.MAX_VALUE / 2 - 1;
                    }
                    if (combileItem.incNode.size() > 0) {
                        dijkstraWithIncNode(clone, combileItem);
                    } else {
                        dijkstraWithoutIncNode(clone, combileItem);
                    }
                }
            }
        }
        //输出
        //统计失败货物数量 和重量
        printResult(combileItems);
    }

    /**
     * 将货物集合组合 封装成组合货物
     *
     * @param combileItems
     * @param itemsIn
     */
    private static void addItems(ArrayList<CombileItem> combileItems, ArrayList<Item> itemsIn) {
        //map key：站点id拼成的字符串 value：同源同宿货物组成的集合
        HashMap<String, ArrayList<Item>> map = new HashMap<>();
        for (Item item : itemsIn) {
            //拼接站点id
            StringBuilder builder = new StringBuilder();
            builder.append(item.srcNode.nodeId + "");
            if (item.incNode.size() > 0) {
                for (Node node : item.incNode) {
                    builder.append(node.nodeId + "");
                }
            }
            builder.append(item.dstNode.nodeId);
            String s = builder.toString();
            //将货物存入map
            if (map.get(s) == null) {
                ArrayList<Item> items = new ArrayList<>();
                items.add(item);
                map.put(s, items);
            } else {
                ArrayList<Item> items = map.get(s);
                items.add(item);
            }
        }
        //获取同源同宿货物的集合
        ArrayList<ArrayList<Item>> list = new ArrayList<>();
        for (String s : map.keySet()) {
            list.add(map.get(s));
        }
        //整合同源同宿 考虑最优容量组合
        combile(list, combileItems);
    }

    /**
     * 整合同源同宿 考虑最优容量组合
     *
     * @param list
     * @param combileItems
     */
    private static void combile(ArrayList<ArrayList<Item>> list, ArrayList<CombileItem> combileItems) {
        for (int i = 0; i < list.size(); i++) {
            //同源同宿货物集合根据货物重量降序排列
            list.get(i).sort(new Comparator<Item>() {
                @Override
                public int compare(Item o1, Item o2) {
                    return (int) (o2.itemWeight - o1.itemWeight);
                }
            });
            //将同源同宿货物按照重量不同存入map key:重量 value：重量相同的货物集合
            HashMap<Double, ArrayList<Item>> map = new HashMap<>();
            ArrayList<Double> weights = new ArrayList<>();//保存各个货物的重量
            for (Item item : list.get(i)) {
                weights.add(item.itemWeight);
                if (map.get(item.itemWeight) == null) {
                    ArrayList<Item> items = new ArrayList<>();
                    items.add(item);
                    map.put(item.itemWeight, items);
                } else {
                    ArrayList<Item> items = map.get(item.itemWeight);
                    items.add(item);
                }
            }
            //整合重量 至等于或尽可能接近列车载重
            while (weights.size() > 0) {
                ArrayList<Double> weightCombile = new ArrayList<>();//保存组合的重量
                double lastWeight = carCapacity;
                //先选取最重的货物
                lastWeight -= weights.get(0);
                weightCombile.add(weights.get(0));
                weights.remove(0);
                //然后选取若干个最轻的货物
                if (weights.size() > 0 && lastWeight > 0) {
                    int endIndex = weights.size() - 1;
                    int startIndex = endIndex;
                    do {
                        lastWeight -= weights.get(startIndex);
                        weightCombile.add(weights.get(startIndex));
                        startIndex--;
                    } while (lastWeight >= 0 && startIndex >= 0);
                    //组合后列车剩余载重<0?
                    if (lastWeight < 0) {
                        //移除weights中被整合的重量
                        startIndex++;
                        weightCombile.remove(weightCombile.size() - 1);
                        for (int j = endIndex; j >= startIndex + 1; j--) {
                            weights.remove(j);
                        }
                    } else {
                        weights.clear();
                    }
                }
                //创建CombileItem并存入
                ArrayList<Item> items = map.get(weightCombile.get(0));
                Item item = items.get(0);
                Node srcNode = item.srcNode;
                Node dstNode = item.dstNode;
                ArrayList<Node> incNode = item.incNode;
                ArrayList<Item> combileItemsList = new ArrayList<>();
                double weightSum = 0.0;
                for (Double weight : weightCombile) {
                    Item item1 = map.get(weight).remove(0);
                    combileItemsList.add(item1);
                    weightSum += item1.itemWeight;
                }
                CombileItem combileItem = new CombileItem(combileItemsList, srcNode, dstNode, weightSum, incNode, new ArrayList<Link>(), new ArrayList<Integer>());
                combileItems.add(combileItem);
            }
        }
    }

    /**
     * 统计失败货物数量和失败重量 打印结果
     *
     * @param combileItems
     */
    private static void printResult(ArrayList<CombileItem> combileItems) {
        //统计失败货物数量和失败重量
        int failItemNum = 0;//失败货物数量
        double failItemWeight = 0;//失败重量
        for (CombileItem combileItem : combileItems) {
            if (combileItem.linkRoute == null) {
                failItemNum += combileItem.items.size();
                failItemWeight += combileItem.itemTotalWeight;
            }
        }
        //打印结果
        System.out.println(failItemNum + "," + String.format("%.3f", failItemWeight));
        for (CombileItem combileItem : combileItems) {
            for (Item item : combileItem.items) {
                System.out.println("G" + item.itemId);
                if (combileItem.linkRoute == null) {
                    System.out.println("null");
                    System.out.println("null");
                } else {

                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < combileItem.linkRoute.size(); i++) {
                        if (i == 0) {
                            builder.append("R" + combileItem.linkRoute.get(i).linkId);
                        } else {
                            builder.append(",R" + combileItem.linkRoute.get(i).linkId);
                        }
                    }
                    System.out.println(builder.toString());

                    StringBuilder builder2 = new StringBuilder();
                    for (int i = 0; i < combileItem.cars.size(); i++) {
                        if (i == 0) {
                            builder2.append(combileItem.cars.get(i));
                        } else {
                            builder2.append("," + combileItem.cars.get(i));
                        }
                    }
                    System.out.println(builder2.toString());
                }
            }
        }
    }

    /**
     * 给包含必经站点的组合货物规划路径
     *
     * @param clone
     * @param combileItem
     */
    private static void dijkstraWithIncNode(double[][] clone, CombileItem combileItem) {
        //将初始站点-必经站点-终止站点放入集合
        ArrayList<Node> nodes = new ArrayList<>();
        nodes.add(combileItem.srcNode);
        for (Node node : combileItem.incNode) {
            nodes.add(node);
        }
        nodes.add(combileItem.dstNode);

        //分段获取路径并组合
        ArrayList<String> routeNodeIdStrs = new ArrayList<>();
        for (int i = 0; i < nodes.size() - 1; i++) {
            //获取路径
            String[] strings = dijkstra(clone, nodes.get(i), nodes.get(i + 1));
            if (strings == null) {
                combileItem.linkRoute = null;
                combileItem.cars = null;
                return;
            }
            //组合
            if (i == 0) {
                routeNodeIdStrs.addAll(Arrays.asList(strings));
            } else {
                routeNodeIdStrs.addAll(Arrays.asList(strings).subList(1, strings.length));
            }
        }
        //判断路径是否有重复节点
        HashSet<String> set = new HashSet<>();
        for (String nodeIdStr : routeNodeIdStrs) {
            set.add(nodeIdStr);
        }
        if (set.size() == routeNodeIdStrs.size()) {
            //无重复节点 判断路径上是否有相同车道 并更新起始节点的拣货员数量 和 经过的边的权重
            String[] strings = new String[routeNodeIdStrs.size()];
            testAndUpdate(routeNodeIdStrs.toArray(strings), combileItem);
        } else {
            combileItem.linkRoute = null;
            combileItem.cars = null;
        }
    }

    /**
     * 给不包含必经站点的组合货物规划路径
     *
     * @param clone
     * @param combileItem
     */
    private static void dijkstraWithoutIncNode(double[][] clone, CombileItem combileItem) {
        //找到最短路径
        String[] routeNodeIdStrs = dijkstra(clone, combileItem.srcNode, combileItem.dstNode);
        if (routeNodeIdStrs == null) {
            combileItem.linkRoute = null;
            combileItem.cars = null;
            return;
        }
        //判断路径上是否有相同车道 并更新起始节点的拣货员数量 和 经过的边的权重
        testAndUpdate(routeNodeIdStrs, combileItem);
    }

    /**
     * 判断路径上是否有相同车道 并更新起始节点的拣货员数量 和 经过的边的权重
     *
     * @param routeNodeIdStrs
     * @param combileItem
     */
    private static void testAndUpdate(String[] routeNodeIdStrs, CombileItem combileItem) {

        linkRoute = new ArrayList<>();//存储路径
        ArrayList<Integer> car = new ArrayList<>();//存储路径对应的列车号
        if (routeNodeIdStrs.length <= 1) {
            combileItem.linkRoute = null;
            combileItem.cars = null;
            return;
        } else {
            //找到路径对应的link
            for (int i = 0; i < routeNodeIdStrs.length - 1; i++) {
                int startNodeId = Integer.parseInt(routeNodeIdStrs[i]);
                int endNodeId = Integer.parseInt(routeNodeIdStrs[i + 1]);
                for (Link link : nodes[startNodeId].relatedLink) {
                    if ((link.srcNode.nodeId == startNodeId && link.dstNode.nodeId == endNodeId)
                            || (link.srcNode.nodeId == endNodeId && link.dstNode.nodeId == startNodeId)) {
                        linkRoute.add(link);
                        break;
                    }
                }
            }
            if (linkRoute.size() != routeNodeIdStrs.length - 1) {
                combileItem.linkRoute = null;
                combileItem.cars = null;
                return;
            }
            //找到路径上可用载重符合的车道交集
            ArrayList<Integer> carIds = CarIntersection(linkRoute, combileItem.itemTotalWeight);
            if (carIds.size() == 0) {
                combileItem.linkRoute = null;
                combileItem.cars = null;
                return;
            } else {
                //使用第一个可用车道
                Integer carId = carIds.get(0);
                //保存经过的车道和更新车道的权重
                for (Link link : linkRoute) {
                    Car carSelect = link.linkCars.get(carId - 1);
                    //更新车道的availWeight
                    carSelect.availWeight = 0;//独占车厢
                    car.add(carSelect.carNo);
                    link.availWeight -= combileItem.itemTotalWeight;
                    //更新link的权重
                    double updateCost = (link.totalWeight - link.availWeight) / link.totalWeight * 100 + 1;
                    link.linkCost = updateCost;
                    matrix[link.srcNode.nodeId][link.dstNode.nodeId] = updateCost;
                    matrix[link.dstNode.nodeId][link.srcNode.nodeId] = updateCost;
                }
                //更新首尾节点的拣货员数量
                combileItem.srcNode.availWorkerNum--;
                combileItem.dstNode.availWorkerNum--;
                //保存组合货物的路径及对应列车号
                combileItem.linkRoute = linkRoute;
                combileItem.cars = car;
            }
        }
    }

    /**
     * 迪杰斯特拉求最短路径
     * 参考：https://blog.csdn.net/qq_34842671/article/details/90083037
     *
     * @param clone
     * @param srcNode
     * @param dstNode
     * @return
     */
    private static String[] dijkstra(double[][] clone, Node srcNode, Node dstNode) {
        //最短路径长度
        double[] shortest = new double[clone.length];
        //判断该点的最短路径是否求出
        int[] visited = new int[clone.length];
        //初始化源节点
        int srcNodeId = srcNode.nodeId;
        int dstNodeId = dstNode.nodeId;
        shortest[srcNodeId] = 0;
        visited[srcNodeId] = 1;

        //存储输出路径
        String[] path = new String[clone.length];

        //初始化输出路径
        for (int i = 0; i < clone.length; i++) {
            path[i] = new String(srcNodeId + "-" + i);
        }

        for (int i = 1; i < clone.length; i++) {
            double min = Double.MAX_VALUE;
            int index = -1;
            for (int j = 0; j < clone.length; j++) {
                //已经求出最短路径的节点不需要再加入计算并判断加入节点后是否存在更短路径
                if (visited[j] == 0 && clone[srcNodeId][j] < min) {
                    min = clone[srcNodeId][j];
                    index = j;
                }
            }
            if (index == -1) {
                return null;
            }
            //更新最短路径
            shortest[index] = min;
            visited[index] = 1;

            //更新从index跳到其它节点的较短路径
            for (int m = 0; m < clone.length; m++) {
                if (visited[m] == 0 && clone[srcNodeId][index] + clone[index][m] < clone[srcNodeId][m]) {
                    clone[srcNodeId][m] = clone[srcNodeId][index] + clone[index][m];
                    path[m] = path[index] + "-" + m;
                }
            }
        }
        //找到最短路径 判断路径上是否有相同车道 并更新起始节点的拣货员数量 和 经过的边的权重
        String[] routeNodeIdStrs = path[dstNodeId].split("-");
        return routeNodeIdStrs;
    }


    /**
     * 求可用车道的交集
     *
     * @param linkRoute
     * @param itemTotalWeight
     * @return
     */
    private static ArrayList<Integer> CarIntersection(ArrayList<Link> linkRoute, double itemTotalWeight) {
        //遍历link集合 形成一个map key：符合载重要求的列车号 value：列车号出现的次数
        ArrayList<Integer> list = new ArrayList<>();
        HashMap<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < linkRoute.size(); i++) {
            ArrayList<Car> cars = linkRoute.get(i).linkCars;
            for (int j = 0; j < cars.size(); j++) {
                int carNo = cars.get(j).carNo;
                if (cars.get(j).availWeight >= itemTotalWeight) {
                    map.put(carNo, map.get(carNo) == null ? 1 : map.get(carNo) + 1);
                }
            }
        }
        //遍历map 返回出现次数等于link数的列车号的集合
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getValue() == linkRoute.size()) {
                list.add(entry.getKey());
            }
        }
        return list;
    }

    //组合货物类
    static class CombileItem {
        ArrayList<Item> items;//组合货物集合
        Node srcNode;//货物起始站点
        Node dstNode; //货物终止站点
        double itemTotalWeight; //组合货物总重量
        ArrayList<Node> incNode;//货物必经站点
        ArrayList<Link> linkRoute;//路径
        ArrayList<Integer> cars;//路径对应的列车号

        public CombileItem(ArrayList<Item> items, Node srcNode, Node dstNode, double itemTotalWeight, ArrayList<Node> incNode, ArrayList<Link> linkRoute, ArrayList<Integer> cars) {
            this.items = items;
            this.srcNode = srcNode;
            this.dstNode = dstNode;
            this.itemTotalWeight = itemTotalWeight;
            this.incNode = incNode;
            this.linkRoute = linkRoute;
            this.cars = cars;
        }
    }

    //站点类
    static class Node {
        int nodeId;//站点id
        ArrayList<Link> relatedLink;//与站点相连的轨道
        int totalWorkerNum;//站点拣货员数量
        int availWorkerNum; //站点可用拣货员数量

        public Node(int nodeId, ArrayList<Link> relatedLink, int totalWorkerNum, int availWorkerNum) {
            this.nodeId = nodeId;
            this.relatedLink = relatedLink;
            this.totalWorkerNum = totalWorkerNum;
            this.availWorkerNum = availWorkerNum;
        }
    }

    //轨道类
    static class Link {
        int linkId;//轨道id
        Node srcNode;//轨道连接的站点1
        Node dstNode; //轨道连接的站点2
        ArrayList<Car> linkCars;// 轨道上的车
        double linkCost;// 轨道权重 可设成1或其他
        double totalWeight;// 轨道总载重
        double availWeight;// 轨道可用载重

        public Link(int linkId, Node srcNode, Node dstNode, ArrayList<Car> linkCars, double linkCost, double totalWeight, double availWeight) {
            this.linkId = linkId;
            this.srcNode = srcNode;
            this.dstNode = dstNode;
            this.linkCars = linkCars;
            this.linkCost = linkCost;
            this.totalWeight = totalWeight;
            this.availWeight = availWeight;
        }
    }

    //列车类
    static class Car {
        int linkId;//列车所在的轨道id
        int carNo;//列车号
        double maxWeight;//列车最大载重
        double availWeight; //列车可用载重

        public Car(int linkId, int carNo, double maxWeight, double availWeight) {
            this.linkId = linkId;
            this.carNo = carNo;
            this.maxWeight = maxWeight;
            this.availWeight = availWeight;
        }
    }

    //货物类
    static class Item {
        int itemId; //货物id
        Node srcNode; //货物起始站点
        Node dstNode; //货物终止站点
        double itemWeight;//货物重量
        ArrayList<Node> incNode; //货物必经站点

        public Item(int itemId, Node srcNode, Node dstNode, double itemWeight, ArrayList<Node> incNode) {
            this.itemId = itemId;
            this.srcNode = srcNode;
            this.dstNode = dstNode;
            this.itemWeight = itemWeight;
            this.incNode = incNode;
        }
    }
}