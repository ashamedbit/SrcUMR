package com.noble.util;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.noble.models.NamePos;
import org.w3c.dom.*;
//import static com.noble.util.RecursionLimiter.emerge;

public final class XmlUtil {
    public static List<Node> MasterList;
    public enum DataAccessType
    {
        @SuppressWarnings("unused") BUFFER_READ, BUFFER_WRITE
    }
    private XmlUtil(){}
    public static List<Node> asList(NodeList n) {
        return n.getLength()==0?
                Collections.emptyList(): new NodeListWrapper(n);
    }
    public static String getNodePos(Node tempNode) {
        return tempNode.getAttributes().item(0).getNodeValue().split(":")[0];
    }
//    public static List<Node> getNodeByName(Node parent, String tag){
//        NodeList children = parent.getChildNodes();
////        Set<Node> targetElements = new HashSet<Node>();
//        List<Node> namedNodes = new LinkedList<Node>(asList(children));
//
//        for(int x = namedNodes.size() - 1; x >= 0; x--)
//        {
//            if(!namedNodes.get(x).getNodeName().equals(tag))
//                namedNodes.remove(x);
//        }
//        if(namedNodes.size()<1){
//            for (int count = 0; count < children.getLength(); count++) {
//                Node childDeep = children.item(count);
//                if(childDeep.getNodeType()==Node.ELEMENT_NODE) {
//                    NodeList deepChildren;
//                    deepChildren = childDeep.getChildNodes();
//                    List<Node> namedDeepNodes = new LinkedList<>(asList(deepChildren));
//                    for(int x = namedDeepNodes.size() - 1; x >= 0; x--)
//                    {
//                        if(!namedDeepNodes.get(x).getNodeName().equals(tag))
//                            namedDeepNodes.remove(x);
//                    }
//                    if(namedDeepNodes.size()>=1)
//                        return namedDeepNodes;
//                }
//            }
//        }
//
//        return  namedNodes;
//    }

    public static List<Node> getNodeByName(Node parent, String tag) {
        List<Node> namedNodes = getNodesBase(parent, tag);
        if (namedNodes.size()>0) {
            return namedNodes;
        }
        NodeList deep = parent.getChildNodes();
        for (int i = 0, len = deep.getLength(); i < len; i++) {
            if (deep.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Node childElement = deep.item(i);
                List<Node> attr = getNodeByName(childElement, tag);
                if ( attr.size()>0)
                    return attr;
            }
        }

        return namedNodes;
    }
    public static List<Node> getNodeByName(Node parent, String tag, Boolean all) {
        MasterList = Collections.emptyList();
        return getNodesByName(parent, tag, all);
    }

    @SuppressWarnings("unused")
    private static List<Node> getNodesByName(Node parent, String tag, Boolean all) {

        List<Node> namedNodes = getNodesBase(parent, tag);
        if (namedNodes.size()>0) {
            MasterList = Stream.of(MasterList, namedNodes)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }
        NodeList deep = parent.getChildNodes();
        for (int i = 0, len = deep.getLength(); i < len; i++) {
            if (deep.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Node childElement = deep.item(i);
                getNodesByName(childElement, tag, all);
            }
        }
        return MasterList;
    }

    private static List<Node> getNodesBase(Node parent, String tag) {
        NodeList children = parent.getChildNodes();
        List<Node> namedNodes = new LinkedList<>(asList(children));
        for(int x = namedNodes.size() - 1; x >= 0; x--)
        {
            if(!namedNodes.get(x).getNodeName().equals(tag))
                namedNodes.remove(x);
        }
        return namedNodes;
    }

    public static NamePos getNamePosTextPair(Node init_node) {
        NodeList nodeList = init_node.getChildNodes();
        NamePos namePos = new NamePos("", "", "", false);
        boolean is_pointer = false;
        Set<String> names = new HashSet<>();
        names.add("decl");
//        System.out.println(init_node.getNodeName()+nodeList.getLength()+nodeList.item(0).getNodeName());
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node tempNode = nodeList.item(count);

            if (tempNode.getNodeType() == Node.ELEMENT_NODE
                    && tempNode.hasAttributes()
                    && tempNode.hasChildNodes()) {
                if (tempNode.getNodeName().equals("name")) {
                    String linePos = getNodePos(tempNode);
                    if(tempNode.getNextSibling()!=null && tempNode.getNextSibling().getNodeType() == Node.ELEMENT_NODE)
                        if (((Element) tempNode.getNextSibling()).getTagName().equals("modifier"))
                        {
                            if(tempNode.getNextSibling().getNodeValue() != null)
                            is_pointer = tempNode.getNextSibling().getNodeValue().equals("*")||tempNode.getNextSibling().getNodeValue().equals("&");
                        }
                    StringBuilder varType = new StringBuilder();
                    try {
//                        NodeList typeList = tempNode.getParentNode().getChildNodes().item(0).getChildNodes();
                        List<Node> typNode = getNodeByName(tempNode.getParentNode(),"type");
                        if (!(typNode.size()<1)){
                            NodeList typeList = typNode.get(0).getChildNodes();
                            for (int c = 0; c < typeList.getLength(); c++) {
                                Node tempType = typeList.item(c);
                                if(tempType.getNodeName().equals("name")){
                                    String Filler = "~";
                                    if(varType.toString().equals("")) Filler = "";
//                                if(tempType.getChildNodes().getLength()) std :: String [ERR]
                                    if(tempType.getLastChild().getNodeType()==Node.ELEMENT_NODE)
                                        varType.append(Filler).append(tempType.getLastChild().getFirstChild().getNodeValue());
                                    else
                                        varType.append(Filler).append(tempType.getLastChild().getNodeValue());
                                }
                            }
                        }
//                      varType = tempNode.getParentNode().getNextSibling().getNextSibling().getChildNodes().item(0).getNodeValue();
                    }
                    catch (NullPointerException | IndexOutOfBoundsException e){
                        varType = new StringBuilder();
                        e.printStackTrace();
                    }
                    if(tempNode.getFirstChild().getNodeType()==Node.ELEMENT_NODE)
//                        tempNode.getFirstChild().getFirstChild().getNodeValue()
                    {
                        List<Node> nameChildren = getNodeByName(tempNode, "name");
                        namePos = new NamePos(nameChildren.get(nameChildren.size()-1).getTextContent(), varType.toString(), linePos, is_pointer);
                    } else
                        namePos = new NamePos(tempNode.getFirstChild().getNodeValue(), varType.toString(), linePos, is_pointer);
                    break;
                }
                else if (tempNode.getNodeName().equals("literal")){
                    return new NamePos(tempNode.getTextContent(),tempNode.getAttributes().getNamedItem("type").getNodeValue(),getNodePos(tempNode),false);
                }
                else if(names.contains(tempNode.getNodeName())){
                    return getNamePosTextPair(tempNode);
                }
            }
        }
        if (init_node.getNodeName().equals("name")&&namePos.getName().equals(""))
            namePos = new NamePos(init_node.getFirstChild().getNodeValue(),"",getNodePos(init_node),false);
        return namePos;
    }
    public static boolean isIndexOutOfBounds(final List<Node> list, int index) {
        return index < 0 || index >= list.size();
    }
    public static List<Node> find_all_nodes(Node unit_node, String tag) {
        Element eElement;
        eElement = (Element) unit_node;
        NodeList allChilds = eElement.getElementsByTagName(tag);
        return asList(allChilds);
    }
    public static ArrayList<NamePos> find_function_parameters(Node encl_function_node){
        ArrayList<NamePos> parameters = new ArrayList<>();
        getNodeByName(getNodeByName(encl_function_node,"parameter_list").get(0), "parameter").forEach(param->{
            Node paramDecl = getNodeByName(param, "decl").get(0);
            List<Node> name_node = getNodeByName(paramDecl,"name");
            if(name_node.size()<1) parameters.add(new NamePos("NoNameParam","", String.valueOf(parameters.size()),false));
            else parameters.add(getNamePosTextPair(name_node.get(0)));
        });
        return parameters;
    }
    public static NamePos find_function_returnvar(Node encl_function_node){
        NamePos ret = new NamePos("", "", "", false);
        if (getNodeByName(encl_function_node,"return").size() < 1) return ret;
        List<Node> exprnodelist = getNodeByName(getNodeByName(encl_function_node,"return").get(0), "expr");

        if (exprnodelist.size() == 0 )
            return ret;
        
        Node exprnode = exprnodelist.get(0);
        List<Node> retvar = getNodeByName(exprnode, "name");
        
        if (retvar.size() < 1)
            return ret;
        //System.out.println("FUCCCK "+ retvar.getTextContent());
        return getNamePosTextPair(retvar.get(0));
    }
    static final class NodeListWrapper extends AbstractList<Node>
            implements RandomAccess {
        private final NodeList list;
        NodeListWrapper(NodeList l) {
            list=l;
        }
        public Node get(int index) {
            return list.item(index);
        }
        public int size() {
            return list.getLength();
        }
    }
}