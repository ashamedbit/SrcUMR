package com.noble;

import com.noble.models.*;
import com.noble.util.OsUtils;
import org.apache.commons.io.IOUtils;
import org.jgrapht.*;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
//import org.jgrapht.alg.shortestpath.BellmanFordShortestPath;
import org.jgrapht.graph.*;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.w3c.dom.NodeList;
//import static com.noble.util.RecursionLimiter.emerge;

import static com.noble.util.XmlUtil.*;

public class Main {

    private static final String jni_native_method_modifier = "native";
    private static final Hashtable<String, SliceProfilesInfo> slice_profiles_info = new Hashtable<>();
    private static final Hashtable<String, SliceProfilesInfo> java_slice_profiles_info = new Hashtable<>();
    private static final Hashtable<String, SliceProfilesInfo> cpp_slice_profiles_info = new Hashtable<>();
    private static final Graph<Encl_name_pos_tuple, DefaultEdge> DG = new DefaultDirectedGraph<>(DefaultEdge.class);
    public static final Hashtable<Encl_name_pos_tuple,ArrayList<String>> detected_violations = new Hashtable<>();

    public static final ArrayList<String> stack = new ArrayList<>();
    public static final ArrayList<String> UMRvar = new ArrayList<>();
    public static HashMap<String,Integer> InnerFieldSetState = new HashMap<String,Integer>();
    public static SliceProfile sourceprofiletoanalyze = null;

    static LinkedList<SliceProfile> analyzed_profiles= new LinkedList<>();
    public static Set<Encl_name_pos_tuple> NativeRetVar = new HashSet<Encl_name_pos_tuple>();

//    public static List<Encl_name_pos_tuple> shortestBellman(Graph<Encl_name_pos_tuple, DefaultEdge> directedGraph, Encl_name_pos_tuple a, Encl_name_pos_tuple b) {
//        BellmanFordShortestPath<Encl_name_pos_tuple, DefaultEdge> bellmanFordShortestPath = new BellmanFordShortestPath<>(directedGraph);
//        return bellmanFordShortestPath.getPath(a, b).getVertexList();
//    }

//    public static void inspectXML(String xmlSource)
//            throws IOException {
//        java.io.FileWriter fw = new java.io.FileWriter("temp.xml");
//        fw.write(xmlSource);
//        fw.close();
//    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        String projectLocation=null;
        String srcML = null;
        File file;
        File tempLoc = null;

        try {

            URI uri = Objects.requireNonNull(Main.class.getClassLoader().getResource("windows/srcml.exe")).toURI();
            if("jar".equals(uri.getScheme())){
                for (FileSystemProvider provider: FileSystemProvider.installedProviders()) {
                    if (provider.getScheme().equalsIgnoreCase("jar")) {
                        try {
                            provider.getFileSystem(uri);
                        } catch (FileSystemNotFoundException e) {
                            // in this case we need to initialize it first:
                            provider.newFileSystem(uri, Collections.emptyMap());
                        }
                    }
                }
            }

            if(args.length>1){
                projectLocation = args[0];
                srcML = args[1];
            }
            else if(args.length==1){
                projectLocation = args[0];
                if(OsUtils.isWindows()){
                    srcML = "windows/srcml.exe";
                }
                else if(OsUtils.isLinux()){
                    srcML = "ubuntu/srcml";
//                    tempLoc = new File(".");
                }
                else {
                    System.err.println("Please specify location of srcML, binary not included for current OS");
                    System.exit(1);
                }
            }
            else {
                System.err.println("Please specify location of project to be analysed");
                System.exit(1);
            }
            ProcessBuilder pb;
            if(args.length>1){
                pb = new ProcessBuilder(srcML, projectLocation, "--position");
            }
            else{
                Path zipPath = Paths.get(Objects.requireNonNull(Main.class.getClassLoader().getResource(srcML)).toURI());
                InputStream in = Files.newInputStream(zipPath);
                //noinspection ConstantConditions
                file = File.createTempFile("PREFIX", "SUFFIX", tempLoc);
                file.setExecutable(true);
                file.deleteOnExit();
                try (FileOutputStream out = new FileOutputStream(file))
                {
                    IOUtils.copy(in, out);
                }
                pb = new ProcessBuilder(file.getAbsolutePath(), projectLocation, "--position");
            }

            String result = IOUtils.toString(pb.start().getInputStream(), StandardCharsets.UTF_8);

//            inspectXML(result);
            System.out.println("Converted to XML, beginning parsing ...");
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(result)));
            for(Node unit_node:asList(doc.getElementsByTagName("unit"))){
                Node fileName = unit_node.getAttributes().getNamedItem("filename");
                if(fileName!=null){
                    String source_file_path = fileName.getNodeValue();
                    if(unit_node.getNodeType() != Node.ELEMENT_NODE){
                        continue;
                    }
                    Hashtable<String, SliceProfile> slice_profiles = new Hashtable<>();
                    analyze_source_unit_and_build_slices(unit_node, source_file_path, slice_profiles);
                    Hashtable<NamePos, Node> function_nodes = find_function_nodes(unit_node);
                    SliceProfilesInfo profile_info = new SliceProfilesInfo(slice_profiles,function_nodes,unit_node);
                    slice_profiles_info.put(source_file_path,profile_info);
                }
            }

//            build_srcml_and_srcslices

            Enumeration<String> e = slice_profiles_info.keys();
            while (e.hasMoreElements()) {
                String key = e.nextElement();
                if(key.endsWith(".java")){
//                    && !key.contains("/test/")
                    java_slice_profiles_info.put(key,slice_profiles_info.get(key));
                }
                else{
                    cpp_slice_profiles_info.put(key,slice_profiles_info.get(key));
                }
            }

//            analyze_sources

            //System.out.println("Buffer analysis start");
            Enumeration<String> profiles_to_analyze = java_slice_profiles_info.keys();
            while (profiles_to_analyze.hasMoreElements()) {
                String file_path = profiles_to_analyze.nextElement();
                SliceProfilesInfo currentSlice = java_slice_profiles_info.get(file_path);
                Enumeration<String> slices_to_analyze = currentSlice.slice_profiles.keys();
                while (slices_to_analyze.hasMoreElements()) {
                    String profile_id = slices_to_analyze.nextElement();
                    SliceProfile profile = currentSlice.slice_profiles.get(profile_id);
                    if(analyzed_profiles.contains(profile)) continue;
                    analyze_slice_profile(profile, java_slice_profiles_info);
                }
            }
            //print_violations();

            System.out.println("UMRS start");
            SliceGenerator.savestateconditional = true;
            ArrayList<Encl_name_pos_tuple> UMRsource_nodes = new ArrayList<>();
            Enumeration<String> umrs_to_analyze = SliceGenerator.UninitVar.keys();
            while (umrs_to_analyze.hasMoreElements()) {
                String var = umrs_to_analyze.nextElement();
                SliceProfile currentSlice = SliceGenerator.UninitVar.get(var);
                UMRsource_nodes.add(new Encl_name_pos_tuple(currentSlice.var_name, currentSlice.function_name, currentSlice.file_name, currentSlice.defined_position));
                taint_umr(currentSlice, cpp_slice_profiles_info, doc);
            }

            //printumr_violations(UMRsource_nodes);
            long end = System.currentTimeMillis();
            System.out.println("Completed in " + (end - start)/1000 + "s");

        } catch (URISyntaxException | IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    private static Node IdentifyFunctionNode (String sig,Document doc)
    {
        Node function_node = null;
        for(Node unit_node:asList(doc.getElementsByTagName("unit"))){
            Node fileName = unit_node.getAttributes().getNamedItem("filename");
            if(fileName!=null){
                String source_file_path = fileName.getNodeValue();
                if(unit_node.getNodeType() != Node.ELEMENT_NODE){
                    continue;
                }
                NodeList funcnodelist = unit_node.getChildNodes();

                for (Node funcnode : asList(funcnodelist))
                {
                    if(funcnode.getNodeType() != Node.ELEMENT_NODE){
                        continue;
                    }

                    if (funcnode.getNodeName().compareTo("function") == 0)
                    {
                        List<Node> potentialmatchlist = getNodeByName(funcnode, "name");
                        for (Node testfunc: potentialmatchlist)
                        {
                            if (testfunc.getTextContent().compareTo(sig) == 0)
                            {
                                function_node = funcnode;
                                return function_node;
                            }
                        }
                        
                    }
                }
            }
        }
        return function_node;
    }

    private static int IdentifyFunctionArgPos (String var,Node funcnode)
    {
        List<Node> arglist = getNodeByName(funcnode, "argument");
        int pos = 0;
        for (Node arg: arglist)
        {
            pos++;
            if (arg.getTextContent().compareTo(var) == 0)
            {
                return pos;
            }
        }
        return pos;
    }

    private static Boolean IsExprInitialised (String var, SliceProfile s, Node expr, int FieldDepth, String alias)
    {
        String varname = "";
        List<Node> namelist = getNodeByName(expr, "name");
        List<Node> operator = getNodeByName(expr, "operator");

        if (operator.size() > 0)
        {
            varname = namelist.get(0).getTextContent();
        }

        if ((varname.compareTo(var) == 0) && (InnerFieldSetState.containsKey(alias)) && (InnerFieldSetState.get(alias)+1 == FieldDepth))
        {
            InnerFieldSetState.remove(alias);
            return true;
        }

        if (UnSetInitField(varname,FieldDepth))
            return true;
            
        return false;
    }

    private static Boolean  IsUMRCarry (String var, Node expr,  int FieldDepth, String alias)
    {
        List<Node> namelist = getNodeByName(expr, "name");
        List<Node> operator = getNodeByName(expr, "operator");

        if (operator.size() > 0)
        {
            for (int i = 1; i< namelist.size(); i++)
            {
                Node n = namelist.get(i);

                if ((var.compareTo(n.getTextContent()) == 0) && (InnerFieldSetState.containsKey(alias)) && (InnerFieldSetState.get(alias)+1 == FieldDepth))
                {
                    InnerFieldSetState.remove(alias);
                    return true;
                }

                if (UnSetInitField(n.getTextContent(), FieldDepth))
                    return true;
                
            }
        }

        return false;
    }

    private static void print_stack_violations(String alias, int FieldDepth)
    {
        System.out.println("");
        System.out.println("Analyzing variable: "+ sourceprofiletoanalyze.var_name+" in file name: "+ sourceprofiletoanalyze.file_name + " At position: "+ sourceprofiletoanalyze.defined_position);
        //System.out.println("UMR finally detected!! For Field "+ alias+" at depth: "+Integer.toString(FieldDepth));
        System.out.println("UMR finally detected!! For Field "+ alias);
        
        for (int k=0; k< stack.size() ;k++)
        {
            System.out.print(stack.get(k) + " ");
            System.out.print(UMRvar.get(k));
            System.out.print(" -> ");
        }
        System.out.println("");
    }

    private static int CheckUninitField(String s, int FieldDepth)
    {
        String[] Fields = s.split("\\.|->");
        if (Fields.length <= 1)
            return -1;
        
        String last = Fields[Fields.length-1];

        if  (InnerFieldSetState.containsKey(last) && (InnerFieldSetState.get(last) == Fields.length +FieldDepth-2))
            return Fields.length + FieldDepth -1;

        return -1;
    }

    private static Boolean UnSetInitField(String s, int FieldDepth)
    {
        String[] Fields = s.split("\\.|->");
        if (Fields.length <= 1)
            return false;
        
        String last = Fields[Fields.length-1];

        if (InnerFieldSetState.containsKey(last) && (InnerFieldSetState.get(last) == Fields.length +FieldDepth-2))
        {
            InnerFieldSetState.remove(last);
            // System.out.println("Removing key!!" + last);
            return true;
        }
        return false;
    }

    private static String RetLastField(String s)
    {
        String[] Fields = s.split("\\.|->");
        String last = Fields[Fields.length-1];
        return last;
    }

    public static HashMap<String, Integer> copy (HashMap<String, Integer> original) 
    {
        try {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(original);

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream in = new ObjectInputStream(bis);
        HashMap<String,Integer> copied = (HashMap<String,Integer>)in.readObject();

        out.close();
        in.close();
        return copied;
        } catch (Exception e) {
            //TODO: handle exception Dont care? Maybe
        }
        return null;
    }

    public static void CheckUMRWithinBlock(Node tocheck, String var, String alias, int FieldDepth, int caller)
    {
        List<Node> retlist = getNodeByName(tocheck, "name");

        for (Node ret: retlist)
        {
            if (((var.compareTo(ret.getTextContent()) == 0) && (InnerFieldSetState.containsKey(alias)) && (InnerFieldSetState.get(alias)+1 == FieldDepth))|| UnSetInitField(ret.getTextContent(),FieldDepth))
            {
                if (caller == 1)
                    System.out.println("Uninitialized value returned: ");
                else if (caller ==2)
                    System.out.println("Uninitialized value compared in IF condition: ");
                print_stack_violations(alias, FieldDepth);
            }
        }
    }

    // Returns if variable is initialized in the block - false , or not -true
    private static Boolean block_analyze(SliceProfile source, String var, int pos, Node toanalyze, Document doc, int FieldDepth, String alias)
    {
        NodeList stmtlist = toanalyze.getChildNodes();
        String newalias = alias; //Store in case modified

        // Iterating through statements   
        for (int i = pos; i< stmtlist.getLength(); i++)
        {
             
            Node tocheck = stmtlist.item(i);
            if ((tocheck.getNodeType() == Node.ELEMENT_NODE) && (tocheck.getNodeName().compareTo("expr_stmt") == 0))
            {
                // System.out.println("Analyzing:   "+ tocheck.getTextContent());
                if (IsUMRCarry(var, tocheck, FieldDepth, alias))
                {
                    print_stack_violations(alias, FieldDepth);
                }

                IsExprInitialised(var,source, tocheck, FieldDepth, alias);

                List<Node> callnode = getNodeByName(tocheck, "call");
                if (callnode.size() == 0)
                    continue;

                List<Node> arglist = getNodeByName(callnode.get(0), "argument");

                // Checking if uninitialized value called in function
                Boolean found =false;
                int newFieldDepth = FieldDepth;
                for (Node arg: arglist)
                {
                    List<Node> argnamelist = getNodeByName(arg, "name");

                    if (argnamelist.size() == 0)
                        continue;

                    String argname = argnamelist.get(0).getTextContent();
                    int argfielddepth = CheckUninitField(argname,FieldDepth);

                    if (argfielddepth > 1)
                        newalias = RetLastField(argname);

                    if (argname.compareTo(var) == 0)
                    {
                        found = true;
                        break;
                    }
                    else if (argfielddepth != -1)
                    {
                        newFieldDepth = argfielddepth;
                        found = true;
                        break;
                    }
                }
                if (found == false)
                    continue;

                // Track uninitalized value across call
                String funcname = getNodeByName(callnode.get(0), "name").get(0).getTextContent();         
                Node encl_function_node = IdentifyFunctionNode(funcname, doc);

                if (encl_function_node == null)
                {
                    System.out.println("Potential function call with Uninitialized values: "+funcname);
                    print_stack_violations(alias, FieldDepth);
                    continue;
                }
                int posnew = IdentifyFunctionArgPos(var,callnode.get(0));
                stack.add(funcname);
                taint_funcarg(source, posnew, encl_function_node, doc, newFieldDepth, newalias);
                stack.remove(funcname);
            }
            else if ((tocheck.getNodeType() == Node.ELEMENT_NODE) && (tocheck.getNodeName().compareTo("if_stmt") == 0))
            {
                Boolean foundelse = false; // When else block is executed atleast one if block must be executed hence we must try all combinations
                List<Node> elsechildren = getNodeByName(tocheck,"else");

                if (elsechildren.size()>0)
                    foundelse =true;

                NodeList ifchildren = tocheck.getChildNodes();
                HashMap<String,Integer> oldUnInitFields = copy(InnerFieldSetState);

                for(Node ifnode: asList(ifchildren))
                {
                    List<Node> conditionlist = getNodeByName(ifnode, "condition");

                    if (conditionlist.size()>0)
                        CheckUMRWithinBlock(conditionlist.get(0), var, alias, FieldDepth,2);

                    List<Node> blockList = getNodeByName(ifnode, "block");
                    if (blockList.size() == 0)
                        continue;

                    Node block = blockList.get(0);
                    Node blockcontent = getNodeByName(block, "block_content").get(0);
                    block_analyze(source, var, 0, blockcontent, doc ,FieldDepth, alias);

                    if (foundelse)
                        block_analyze(source, var, i+1, toanalyze, doc ,FieldDepth, alias);
                    
                    InnerFieldSetState = copy(oldUnInitFields);
                }

                if (foundelse) // Already analyzed everything
                    return true; 
            }
            else if((tocheck.getNodeType() == Node.ELEMENT_NODE) && (tocheck.getNodeName().compareTo("return") == 0))
            {
                CheckUMRWithinBlock(tocheck, var, alias, FieldDepth,1);
            }
                    
        }
        return true;   // Continue tracking this variable
    }

    private static void taint_funcarg (SliceProfile source, int pos, Node funcnode, Document doc, int FieldDepth, String alias)
    {
        List<Node> parameterlist = getNodeByName(funcnode,"parameter");

        if (parameterlist.size() < pos)
            return;

        Node varnode = parameterlist.get(pos-1);
        String var = getNodeByName(varnode, "name").get(0).getTextContent();
        UMRvar.add(var);
        
        NodeList stmtlist = funcnode.getChildNodes();

        //Iterating through function statements   
        for (Node n: asList(stmtlist))
        {
            if ((n.getNodeType() == Node.ELEMENT_NODE) && (n.getNodeName().compareTo("block") == 0))
            {
                Node blocknode = n;
                NodeList blocknodelist = blocknode.getChildNodes();

                for (Node blockcontentnode: asList(blocknodelist))
                {
                    if ((blockcontentnode.getNodeType() == Node.ELEMENT_NODE) && (blockcontentnode.getNodeName().compareTo("block_content") == 0))
                    {
                        block_analyze(source, var, 0, blockcontentnode, doc, FieldDepth, alias);
                    }
                }
            }
        }
        UMRvar.remove(var);
    }

    private static void find_all_nodes(Node n, List<Node> res)
    {
        NodeList children =n.getChildNodes();
        for (Node child: asList(children))
        {
            if(child.getNodeType() == Node.ELEMENT_NODE)
            {
                if (child.getNodeName().compareTo("decl_stmt") == 0)
                {
                    res.add(child);
                    continue; 
                }
                find_all_nodes(child, res);
            }     
        }
    }


    private static void taint_umr(SliceProfile source, Hashtable<String,SliceProfilesInfo> cpp_slice_profiles_info, Document doc)
    {
        String sourcevarname = source.var_name;
        String definedposition = source.defined_position;
        Node function_node = source.function_node;
        InnerFieldSetState = copy(source.InnerFields);
        InnerFieldSetState.put(sourcevarname,0); //Just define default

        sourceprofiletoanalyze = source;

        stack.add(source.function_name);
        UMRvar.add(sourcevarname);

        // System.out.println("Analyzing variable: "+ sourcevarname+" in file name: "+ source.file_name + " At position: "+ definedposition);
        // System.out.println("Uninitialized fields: "+ InnerFieldSetState);

        List<Node> declstmt = new ArrayList<Node>();
        find_all_nodes(function_node, declstmt);

        //Iterating through declaration statements
        for (int i=0; i< declstmt.size(); i++)
        {
            Node decl = declstmt.get(i);
            Node blockcontentnode = decl.getParentNode();
            Node namenode = getNodeByName(decl, "name").get(0);
            String name = namenode.getTextContent();
            String alias = name;

            if (name.compareTo(sourcevarname) == 0)
            {
                block_analyze(source, sourcevarname, i+1, blockcontentnode, doc , 1, alias);
                break;
            }
            
        }
                   

        stack.remove(source.function_name);
        UMRvar.remove(sourcevarname);
    }


    private static void printumr_violations(ArrayList<Encl_name_pos_tuple> source_nodes) {
        
        int violations_count = 0;
        for(Encl_name_pos_tuple source_node: source_nodes){
            Iterator<SliceProfile> it = SliceGenerator.UMRSink.iterator();
            while (it.hasNext()) {
                SliceProfile ViolationSlice = it.next();
                Encl_name_pos_tuple violated_node_pos_pair = new Encl_name_pos_tuple(ViolationSlice.var_name, ViolationSlice.function_name, ViolationSlice.file_name, ViolationSlice.defined_position);
                AllDirectedPaths<Encl_name_pos_tuple,DefaultEdge> allDirectedPaths = new AllDirectedPaths<>(DG);
                List<GraphPath<Encl_name_pos_tuple,DefaultEdge>> requiredPath = allDirectedPaths.getAllPaths(source_node, violated_node_pos_pair, true, null);
                if(!requiredPath.isEmpty()){
                    System.out.print("Possible UMR path : ");

                    requiredPath.get(0).getVertexList().forEach(x->System.out.print(x + " -> "));
                    System.out.println();
//                    shortestBellman(DG,source_node, violated_node_pos_pair)
//                            .forEach(x->System.out.print(x + " -> "));
                }
            }
        }
        System.out.println("Detected UMR violations "+ violations_count);
        if(violations_count>0) System.exit(1);
    }

    private static void print_violations() {
        ArrayList<Encl_name_pos_tuple> source_nodes = new ArrayList<>();
        for(Encl_name_pos_tuple node:DG.vertexSet()){
            if (DG.inDegreeOf(node) == 0)
            source_nodes.add(node);
        }
        int violations_count = 0;
        for(Encl_name_pos_tuple source_node: source_nodes){
            Enumeration<Encl_name_pos_tuple> violationE = detected_violations.keys();
            while (violationE.hasMoreElements()) {
                Encl_name_pos_tuple violated_node_pos_pair = violationE.nextElement();
                ArrayList<String> violations = detected_violations.get(violated_node_pos_pair);
                AllDirectedPaths<Encl_name_pos_tuple,DefaultEdge> allDirectedPaths = new AllDirectedPaths<>(DG);
                List<GraphPath<Encl_name_pos_tuple,DefaultEdge>> requiredPath = allDirectedPaths.getAllPaths(source_node, violated_node_pos_pair, true, null);
                if(!requiredPath.isEmpty()){
                    System.out.print("Possible out-of-bounds operation path : ");

                    requiredPath.get(0).getVertexList().forEach(x->System.out.print(x + " -> "));
                    System.out.println();
//                    shortestBellman(DG,source_node, violated_node_pos_pair)
//                            .forEach(x->System.out.print(x + " -> "));
                    violations.forEach(violation-> System.err.println("Reason : "+violation));
                    violations_count = violations_count + violations.size();
                }
            }
        }
        System.out.println("No of files analyzed "+ java_slice_profiles_info.size());
        System.out.println("Detected violations "+ violations_count);
    }

    private static void analyze_slice_profile(SliceProfile profile, Hashtable<String, SliceProfilesInfo> raw_profiles_info) {
        analyzed_profiles.add(profile);
//        try{
//            emerge();
//        } catch (Exception e) {
//            return;
//        }

//                  step-01 : analyse cfunctions of the slice variable

        Encl_name_pos_tuple encl_name_pos_tuple;
        Enumeration<String> cfunction_k = profile.cfunctions.keys();
        while (cfunction_k.hasMoreElements()) {
            String cfunction_name = cfunction_k.nextElement();
            cFunction cfunction = profile.cfunctions.get(cfunction_name);
            int arg_pos_index = cfunction.getArg_pos_index();
            String cfunction_pos = cfunction.getCfunction_pos();
            String encl_function_name = cfunction.getCurrent_function_name();
            Node encl_function_node = cfunction.getCurrent_function_node();
            encl_name_pos_tuple = new Encl_name_pos_tuple(profile.var_name,encl_function_name,profile.file_name,profile.defined_position);
            analyze_cfunction(cfunction_name, cfunction_pos, arg_pos_index, profile.type_name, encl_function_node, encl_name_pos_tuple, raw_profiles_info);
        }
        encl_name_pos_tuple = new Encl_name_pos_tuple(profile.var_name,profile.function_name,profile.file_name,profile.defined_position);
        if (!DG.containsVertex(encl_name_pos_tuple))
        DG.addVertex(encl_name_pos_tuple);

//                  step-02 : analyze data dependent vars of the slice variable

        for(NamePos dv: profile.dependent_vars){
            String dvar = dv.getName();
            String dvar_encl_function_name = dv.getType();
            String dvar_pos = dv.getPos();
            Hashtable<String, SliceProfile> source_slice_profiles = raw_profiles_info.get(profile.file_name).slice_profiles;
            String key = dvar + "%" + dvar_pos + "%" + dvar_encl_function_name + "%" + profile.file_name;
            if(!source_slice_profiles.containsKey(key)) {
//                not capturing struct/class var assignments
                continue;
            }
            SliceProfile dvar_slice_profile = source_slice_profiles.get(key);
            Encl_name_pos_tuple dvar_name_pos_tuple = new Encl_name_pos_tuple(dvar_slice_profile.var_name, dvar_slice_profile.function_name, dvar_slice_profile.file_name, dvar_slice_profile.defined_position);

            if (NativeRetVar.contains(dvar_name_pos_tuple))
            {
                System.out.println("Dependant variable: "+dvar_slice_profile.var_name + " File: "+ dvar_slice_profile.file_name + " Position: "+ dvar_slice_profile.defined_position);
                System.out.println("UMR DETECTED!!!");
            }

            if(has_no_edge(encl_name_pos_tuple,dvar_name_pos_tuple)){
                analyze_slice_profile(dvar_slice_profile,raw_profiles_info);
            }
        }

//                  step-03 : analyze if given function node is a native method

        if(!profile.function_name.equals("GLOBAL") && profile.cfunctions.size()<1){
            Node encl_function_node = profile.function_node;
            if (is_function_of_given_modifier(encl_function_node, jni_native_method_modifier)){
               analyze_native_function(profile, raw_profiles_info, encl_function_node, encl_name_pos_tuple);
            }
        }

//                  step-04 : check and add buffer reads and writes for this profile

        if(profile.file_name.endsWith(".cpp")||profile.file_name.endsWith(".c")||profile.file_name.endsWith(".cc")){
            for(SliceVariableAccess var_access:profile.used_positions){
                for(Tuple access:var_access.write_positions){
                    if(DataAccessType.BUFFER_WRITE == access.access_type){
                        ArrayList<String> currentArr;
                        if(detected_violations.containsKey(encl_name_pos_tuple))
                        currentArr = new ArrayList<>(detected_violations.get(encl_name_pos_tuple));
                        else
                        currentArr = new ArrayList<>();
                        currentArr.add("Buffer write at " + access.access_pos);
                        detected_violations.put(encl_name_pos_tuple,currentArr);
                    }
                }
            }
        }

    }

    private static void analyze_cfunction(String cfunction_name, String cfunction_pos, int arg_pos_index, String var_type_name, Node encl_function_node, Encl_name_pos_tuple encl_name_pos_tuple, Hashtable<String, SliceProfilesInfo> slice_profiles_info) {
        LinkedList <SliceProfile> dependent_slice_profiles = find_dependent_slice_profiles(cfunction_name, arg_pos_index, var_type_name, encl_function_node, slice_profiles_info);
        dependent_slice_profiles.forEach(dep_profile->{
            Encl_name_pos_tuple dep_name_pos_tuple = new Encl_name_pos_tuple(dep_profile.var_name, dep_profile.function_name, dep_profile.file_name, dep_profile.defined_position);
            if(!has_no_edge(encl_name_pos_tuple,dep_name_pos_tuple)) return;
            if(analyzed_profiles.contains(dep_profile)) return;
            analyze_slice_profile(dep_profile, slice_profiles_info);
        });
        if(dependent_slice_profiles.size()<1){
            if(cfunction_name.equals("strcpy") || cfunction_name.equals("strncpy") || cfunction_name.equals("memcpy")){
                DG.addVertex(encl_name_pos_tuple);
                ArrayList<String> cErrors = new ArrayList<>();
                cErrors.add("Use of " + cfunction_name + " at " + cfunction_pos);
                detected_violations.put(encl_name_pos_tuple, cErrors);
            }
        }
    }

    @SuppressWarnings("unused")
    private static LinkedList<SliceProfile> find_dependent_slice_profiles(String cfunction_name, int arg_pos_index, String type_name, Node current_function_node, Hashtable<String, SliceProfilesInfo> java_slice_profiles_info) {
        LinkedList <SliceProfile> dependent_slice_profiles = new LinkedList<>();
        Enumeration<String> profiles_to_analyze = java_slice_profiles_info.keys();
        while (profiles_to_analyze.hasMoreElements()) {
            String file_path = profiles_to_analyze.nextElement();
            SliceProfilesInfo profile_info = java_slice_profiles_info.get(file_path);
                for (cFunction cfunction: find_possible_functions(profile_info.function_nodes, cfunction_name, arg_pos_index, current_function_node)
                ) {
                    NamePos param = cfunction.getFunc_args().get(arg_pos_index-1);
                    String param_name = param.getName();
                    String param_pos = param.getPos();
                    String key = param_name + "%" + param_pos + "%" + cfunction_name + "%" + file_path;
                    if(!profile_info.slice_profiles.containsKey(key)) continue;
                    dependent_slice_profiles.add(profile_info.slice_profiles.get(key));
                }
            }
        return dependent_slice_profiles;
    }


    private static void analyze_native_function(SliceProfile profile, Hashtable<String, SliceProfilesInfo> profiles_info, Node encl_function_node, Encl_name_pos_tuple encl_name_pos_tuple) {
        Node encl_unit_node = profiles_info.get(profile.file_name).unit_node;
        String jni_function_name = profile.function_name;
        String jni_arg_name = profile.var_name;
        ArrayList<NamePos> params = find_function_parameters(encl_function_node);
        int index = 0;
        for(NamePos par:params){
            if(par.getName().equals(jni_arg_name)) break;
            index++;
        }
        int jni_arg_pos_index = index + 2;
        String clazz_name = getNodeByName(getNodeByName(encl_unit_node,"class").get(0),"name").get(0).getTextContent();
        String jni_function_search_str = "_" + clazz_name + "_" + jni_function_name;

        Enumeration<String> profiles_to_analyze = cpp_slice_profiles_info.keys();
        while (profiles_to_analyze.hasMoreElements()) {
            String file_path = profiles_to_analyze.nextElement();
            SliceProfilesInfo profile_info = cpp_slice_profiles_info.get(file_path);
//            profile_info.function_nodes.forEach((func,function_node)->{
            Enumeration<NamePos> functions_to_analyze = profile_info.function_nodes.keys();
            while (functions_to_analyze.hasMoreElements()) {
                NamePos func = functions_to_analyze.nextElement();
                Node function_node = profile_info.function_nodes.get(func);
                String function_name = func.getName();
                if(!function_name.toLowerCase().endsWith(jni_function_search_str.toLowerCase())) continue;
                ArrayList<NamePos> function_args = find_function_parameters(function_node);

                NamePos retVar = find_function_returnvar(function_node);
                //System.out.println("Returned variable name: "+ retVar.getName() + " Position: " + retVar.getPos() + " function name: "+ function_name + " file path: "+ file_path);
                String retkey = retVar.getName() + "%" + retVar.getPos() +"%" + function_name + "%" + file_path;
                

                if(function_args.size()<1 || jni_arg_pos_index>function_args.size()) continue;
                NamePos arg = function_args.get(jni_arg_pos_index);
                String key = arg.getName() + "%" + arg.getPos() +"%" + function_name + "%" + file_path;
                SliceProfile possible_slice_profile = null;
                Enumeration<String> profiles_prob = profile_info.slice_profiles.keys();
                while (profiles_prob.hasMoreElements()) {
                    String cpp_profile_id = profiles_prob.nextElement();
                    SliceProfile cpp_profile = profile_info.slice_profiles.get(cpp_profile_id);
                    if(cpp_profile_id.equals(key)) {possible_slice_profile = cpp_profile; break;}
                }

                if (!retVar.getName().equals(""))
                {
                    Enumeration<String> retvarprofile = profile_info.slice_profiles.keys();
                    while (retvarprofile.hasMoreElements()) {
                        String cpp_profile_id = retvarprofile.nextElement();
                        SliceProfile cpp_profile = profile_info.slice_profiles.get(cpp_profile_id);
                        //System.out.println("Comparing keys :"+ cpp_profile_id);
                        if(cpp_profile.RetPos == Integer.parseInt(retVar.getPos())) { 
                            //System.out.println("Ret VAR variable: "+cpp_profile.var_name + " File: "+ cpp_profile.file_name + " Position: "+ cpp_profile.defined_position);
                            NativeRetVar.add(new Encl_name_pos_tuple(cpp_profile.var_name, cpp_profile.function_name, cpp_profile.file_name, cpp_profile.defined_position)) ;
                            break;
                        }
                    }
                }
                if(possible_slice_profile == null) continue;
                Encl_name_pos_tuple analyzed_name_pos_tuple = new Encl_name_pos_tuple(possible_slice_profile.var_name, possible_slice_profile.function_name, possible_slice_profile.file_name, possible_slice_profile.defined_position);
                if (!has_no_edge(encl_name_pos_tuple, analyzed_name_pos_tuple)) continue;
                if (analyzed_profiles.contains(possible_slice_profile)) continue;
                analyze_slice_profile(possible_slice_profile,cpp_slice_profiles_info);
            }
        }
    }

    private static boolean is_function_of_given_modifier(Node encl_function_node, String jni_native_method_modifier) {
        List<Node> specifiers =  getNodeByName(encl_function_node,"specifier");
        for (Node n : specifiers) {
            String nodeName = n.getTextContent();
            if (jni_native_method_modifier.equals(nodeName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean has_no_edge(Encl_name_pos_tuple source_name_pos_tuple, Encl_name_pos_tuple target_name_pos_tuple) {
        if(source_name_pos_tuple.equals(target_name_pos_tuple)) return false;
        if(!DG.containsVertex(source_name_pos_tuple))
        DG.addVertex(source_name_pos_tuple);
        if(!DG.containsVertex(target_name_pos_tuple))
        DG.addVertex(target_name_pos_tuple);

//        if(DG.containsEdge(source_name_pos_tuple,target_name_pos_tuple)) return false;
//        if(!DG.containsVertex(target_name_pos_tuple))
//        for( Encl_name_pos_tuple node : DG.vertexSet()){
//           if (node.equals(target_name_pos_tuple)){
//               target_name_pos_tuple = node;
//               break;
//            }
//        }
        if(DG.containsEdge(source_name_pos_tuple,target_name_pos_tuple))
            return false;

        DG.addEdge(source_name_pos_tuple,target_name_pos_tuple);
        return true;
    }

    private static LinkedList<cFunction> find_possible_functions(Hashtable<NamePos, Node> function_nodes, String cfunction_name, int arg_pos_index, Node encl_function_node) {
        LinkedList<cFunction> possible_functions = new LinkedList<>();
        Enumeration<NamePos> e = function_nodes.keys();
        while (e.hasMoreElements()) {
            NamePos key = e.nextElement();
            Node possible_function_node = function_nodes.get(key);
            String function_name = key.getName();
            if(!function_name.equals(cfunction_name)) continue;
            
            ArrayList<NamePos> func_args = find_function_parameters(possible_function_node);
            if(func_args.size()==0 || arg_pos_index>func_args.size()) continue;

            int arg_index = arg_pos_index -1;
            String param_name = func_args.get(arg_index).getName();
            if(param_name.equals("")) continue;

            if(!validate_function_against_call_expr(encl_function_node, cfunction_name, arg_index, func_args)) continue;

            possible_functions.add(new cFunction(arg_index,function_name,"",encl_function_node,func_args));
        }
        return possible_functions;
    }

    @SuppressWarnings("unused")
    private static boolean validate_function_against_call_expr(Node encl_function_node, String cfunction_name, int arg_index, ArrayList<NamePos> func_args) {
        List<Node> call_argument_list;
        for (Node call:getNodeByName(encl_function_node,"call",true)){
            String function_name = getNamePosTextPair(call).getName();
            if(!cfunction_name.equals(function_name)) continue;
            call_argument_list = getNodeByName(getNodeByName(call, "argument_list").get(0),"argument");
            if(call_argument_list.size()!=func_args.size()) continue;
            return true;
        }
        return false;
    }

    private static void analyze_source_unit_and_build_slices(Node unit_node, String source_file_path, Hashtable<String, SliceProfile> slice_profiles) {
        SliceGenerator slice_generator = new SliceGenerator(unit_node,source_file_path,slice_profiles);
        slice_generator.generate();
    }
//    private static boolean isElementOfInterest(Node nNode)
//    {
//        System.out.println(nNode.getNodeName());
//        return nNode.getNodeName().equals("function") || nNode.getNodeName().equals("function_decl") || nNode.getNodeName().equals("constructor");
//    }


    private static Hashtable<NamePos, Node> find_function_nodes(Node unit_node) {
        Hashtable<NamePos, Node> function_nodes = new Hashtable<>();
//        Element eElement = (Element) unit_node;
        List<Node> fun1 = getNodeByName(unit_node,"function", true);
        List<Node> fun2 = getNodeByName(unit_node,"function_decl", true);
        List<Node> fun3 = getNodeByName(unit_node,"constructor", true);
        List<Node> fun4 = getNodeByName(unit_node,"destructor", true);

        List<Node> funList = Stream.of(fun1, fun2, fun3, fun4)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        for(Node node:funList){
            function_nodes.put(getNamePosTextPair(node),node);
        }
        return function_nodes;
    }

}
