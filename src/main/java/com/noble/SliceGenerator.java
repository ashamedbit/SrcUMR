package com.noble;

import com.noble.models.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

import javax.lang.model.util.ElementScanner6;

import static com.noble.util.XmlUtil.*;
import static com.noble.util.XmlUtil.getNamePosTextPair;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SliceGenerator {
    String GLOBAL;
    String src_type;
    String identifier_separator;
    Node unit_node;
    Hashtable<String, SliceProfile> slice_profiles;
    Hashtable<String, Hashtable<String, SliceProfile>>  local_variables;
    Hashtable<String, Hashtable<String, SliceProfile>> global_variables;
    String [] declared_pointers;
    String file_name;
    String current_function_name;
    Node current_function_node;
    static DetectUMR a = new DetectUMR();
    static Hashtable<String, SliceProfile> UninitVar = new Hashtable<String,SliceProfile>();
    static Set<SliceProfile> UMRSink = new HashSet<SliceProfile>();
    static Set<String> functionretUMR = new HashSet<String>();
    static Boolean savestateconditional = true;
    static boolean donotsavestate = false;

    public SliceGenerator(Node unit_node, String file_name, Hashtable<String, SliceProfile> slice_profiles){
        this.unit_node = unit_node;
        this.slice_profiles = slice_profiles;
        this.local_variables = new Hashtable<>();
        this.global_variables = new Hashtable<>();
        this.declared_pointers = new String[]{};
        this.file_name = file_name;
        this.current_function_name = "";
        this.current_function_node = null;
        this.GLOBAL = "GLOBAL";
        this.identifier_separator = "[^\\w]+";
        this.src_type = null;
    }

    public void generate(){
        String langAttribute = this.unit_node.getAttributes().getNamedItem("language").getNodeValue();
        src_type = langAttribute;
        if (langAttribute.equals("Java")) {
            this.analyzeJavaSource();
        }
        else if (langAttribute.equals("C++") || langAttribute.equals("C")) {
            this.analyzeCPPSource(unit_node);
        }
    }

    private void analyzeJavaSource() {
        for(Node class_node:find_all_nodes(this.unit_node,"class"))
            this.analyzeJavaClass(class_node);
    }

    public void analyzeCPPSource(Node unit_node) {
        if(unit_node==null)
            unit_node= this.unit_node;
        NodeList doc = unit_node.getChildNodes();
        for (int count = 0; count < doc.getLength(); count++) {
            Node node = doc.item(count);
            String node_tag = node.getNodeName();
            switch (node_tag) {
                case "decl_stmt":
                    this.analyzeGlobalDecl(node);
                    break;
                case "extern":
                    this.analyzeExternFunction(node);
                    break;
                case "namespace":
                    this.analyzeNamespace(node);
                    break;
                case "class":
                    this.analyzeCppClass(node);
                case "struct":
                    this.analyzeStruct(node);
                    break;
//                case "typedef":
//                    this.analyzeTypeDef(node);
//                    break;
                case "function_decl":
                case "function":
                    this.local_variables = new Hashtable<>();
                    this.analyzeFunction(node);
                    break;
            }
        }
    }

    private void analyzeNamespace(Node namespace_node){
        List<Node> block = getNodeByName(namespace_node, "block");

        if (block.size() == 0)
            return;
            
        analyzeCPPSource(block.get(0));
    }

    private void analyzeStruct(Node struct_node){
//        List<Node> block = getNodeByName(struct_node, "block");
//      TODO analyze struct body
        NamePos struct_type_name_pos = getNamePosTextPair(struct_node);
        String struct_type_name = struct_type_name_pos.getType();
        if (struct_type_name.equals("")) return;
        List<Node> struct_var_name_pos_temp_list = getNodeByName(struct_node, "decl");

        if (struct_var_name_pos_temp_list.size()==0)
            return;

        Node struct_var_name_pos_temp = struct_var_name_pos_temp_list.get(0);
        NamePos struct_var_name_pos = getNamePosTextPair(struct_var_name_pos_temp);
        if(struct_var_name_pos.getName().equals("")) return;
        String struct_var_name = struct_var_name_pos.getName();
        String struct_pos = struct_var_name_pos.getPos();
        String slice_key = struct_var_name + "%" + struct_pos + "%" + GLOBAL + "%" + file_name;
        SliceProfile profile = new SliceProfile(file_name, GLOBAL, struct_var_name, struct_type_name, struct_pos);
        slice_profiles.put(slice_key,profile);
        Hashtable<String, SliceProfile> structProfile = new Hashtable<>();
        structProfile.put(struct_var_name,profile);
        global_variables.put(struct_var_name,structProfile);

        this.a.RecurseClass(struct_node,"rand");
        this.a.FindUninitialisedConstructors();
        // this.a.printData();

    }

    private void analyzeCppClass(Node root){
        this.a.RecurseClass(root,"rand");
        this.a.FindUninitialisedConstructors();
    }

    private void analyzeJavaClass(Node class_node) {
        NodeList nodeList = class_node.getChildNodes();
        NodeList doc= null;
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node node = nodeList.item(count);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && node.hasChildNodes()) {
                doc = node.getChildNodes();
            }
        }
        assert doc!=null;
        for (int count = 0; count < doc.getLength(); count++) {
            Node node = doc.item(count);
            String node_tag = node.getNodeName();
            switch (node_tag) {
                case "decl_stmt":
                    this.analyzeGlobalDecl(node);
                    break;
                case "static":
                    this.analyzeStaticBlock(node);
                    break;
                case "class":
                    this.analyzeJavaClass(node);
                    break;
                case "function_decl":
                case "function":
                case "constructor":
                    this.local_variables = new Hashtable<>();
                    this.analyzeFunction(node);
                    break;
            }
        }
    }

    private void analyzeGlobalDecl(Node nodeTemp) {
        NamePos namePos = getNamePosTextPair(nodeTemp);
        String slice_key = namePos.getName() + "%" + namePos.getPos() + "%" + this.GLOBAL + "%" + this.file_name;
        SliceProfile slice_profile = new SliceProfile(this.file_name, this.GLOBAL, namePos.getName(), namePos.getType(), namePos.getPos());
        this.slice_profiles.put(slice_key,slice_profile);
        Hashtable<String, SliceProfile> nameProfile = new Hashtable<>();
        nameProfile.put(namePos.getName(),slice_profile);
        this.global_variables.put(namePos.getName(),nameProfile);
    }

    private void analyzeStaticBlock(Node static_block) {
        List<Node> block = getNodeByName(static_block, "block");
        current_function_name = GLOBAL;
        current_function_node = static_block;
        analyzeBlock(block.get(0));
        current_function_name = null;
        current_function_node = null;
    }

    private void analyzeExternFunction(Node extern_node) {
        NodeList doc = extern_node.getChildNodes();
        for (int count = 0; count < doc.getLength(); count++) {
            Node node = doc.item(count);
            String node_tag = node.getNodeName();
            if (node_tag.equals("function_decl") || node_tag.equals("function")){
                this.local_variables= new Hashtable<>();
                this.analyzeFunction(node);
            }
        }
    }

    private void analyzeFunction(Node function) {
        NamePos function_name = getNamePosTextPair(function);
        this.current_function_name = function_name.getName();
        this.current_function_node = function;
        List<Node> param = getNodeByName(function, "parameter");
        for (Node node : param) {
            analyzeParam(node);
        }
        List<Node> block_list = getNodeByName(function, "block");
        if(block_list.size()>0) {
            Node block = block_list.get(0);
            analyzeBlock(block);
        }
        this.current_function_name = null;
        this.current_function_node = null;
    }

    private void analyzeBlock(Node block) {
        NodeList iterBlock = getNodeByName(block, "block_content").get(0).getChildNodes();
        for(Node stmt : asList(iterBlock)){
            String stmt_tag = stmt.getNodeName();
            switch (stmt_tag) {
                case "expr_stmt":
                    analyzeExprStmt(stmt);
                    break;
                case "decl_stmt":
                    analyzeDeclStmt(stmt);
                    break;
                case "if_stmt":
                    analyzeIfStmt(stmt);
                    break;
                case "for":
                    analyzeForStmt(stmt);
                    break;
                case "while":
                    analyzeWhileStmt(stmt);
                    break;
                case "return":
                    analyzeReturnStmt(stmt);
                    break;
                case "try":
                    analyzeTryBlock(stmt);
                    break;
                case "switch":
                    analyzeSwitchStmt(stmt);
                    break;
                case "case":
                    analyzeCaseStmt(stmt);
                    break;
            }
        }
    }

    private void analyzeDeclStmt(Node stmt) {
        analyzeDecl(getNodeByName(stmt,"decl"));
    }

    private void analyzeDecl(List<Node> decllist){
        String type = "";
        for ( var i=0; i < decllist.size(); i++)
        {
            Node decl = decllist.get(i);
            NamePos namePos = getNamePosTextPair(decl);

            if (type.equals(""))
            {
                type = namePos.getType();
            }

            String slice_key = namePos.getName() + "%" + namePos.getPos() + "%" + this.current_function_name + "%" + this.file_name;
            SliceProfile slice_profile = new SliceProfile(this.file_name, this.current_function_name, namePos.getName(), type, namePos.getPos(), this.current_function_node);
            
            if (this.file_name.endsWith(".cpp") && !decl.getParentNode().getNodeName().equals("parameter") && isUninitialized(decl,slice_profile))
            {
                slice_profile.setUninitialized();
                System.out.println(" Uninitialized: " + namePos.getName() + " filename " + this.file_name);
                UninitVar.put(namePos.getName(), slice_profile);
            }
            
            this.slice_profiles.put(slice_key,slice_profile);
            Hashtable<String, SliceProfile> nameProfile = new Hashtable<>();
            nameProfile.put(namePos.getName(),slice_profile);
            local_variables.put(namePos.getName(),nameProfile);
            List<Node> expr_temp = getNodeByName(decl, "expr");
            if(expr_temp.size() <1) continue;
            List<Node> init_expr = asList(expr_temp.get(0).getChildNodes());

            for(Node expr: init_expr){
                if (local_variables.containsKey(expr.getTextContent()) && !local_variables.get(expr.getTextContent()).get(expr.getTextContent()).initialized)
                {
                    slice_profile.setUninitialized();
                    System.out.println(" Declaration is uninitialized: " + namePos.getName() + " filename " + this.file_name);
                    UninitVar.put(namePos.getName(), slice_profile);
                }
                else if (global_variables.containsKey(expr.getTextContent()) && !global_variables.get(expr.getTextContent()).get(expr.getTextContent()).initialized)
                {
                    slice_profile.setUninitialized();
                    System.out.println(" Declaration is uninitialized: " + namePos.getName() + " filename " + this.file_name);
                    UninitVar.put(namePos.getName(), slice_profile);
                }
                if (analyze_update(namePos, expr)) continue;
            }
            List<Node> argument_list_temp = getNodeByName(decl, "argument_list");
            if(argument_list_temp.size()<1) continue;
            List<Node> argument_list = getNodeByName(argument_list_temp.get(0),"argument");
            for (Node arg_expr: argument_list){
                List<Node> expr_temp_f = getNodeByName(arg_expr, "expr");
                if(expr_temp_f.size()<1)continue;
                for(Node expr: asList(expr_temp_f.get(0).getChildNodes())){
                    if (analyze_update(namePos, expr)) continue;
                }
            }
        }

    }

    private boolean isUninitialized(Node decl, SliceProfile profile)
    {
        String classname = getNodeByName(decl,"type").get(0).getTextContent();
        List<Node> initializer = getNodeByName(decl,"init");
        String ArgsSig = "";

        if (this.a.IsClass(classname) && (initializer.size()>0))
        {
            ArgsSig = "(";
            List<Node> constructor = getNodeByName(initializer.get(0),"call");
            if (constructor.size() > 0)
            {
                List<Node> args = getNodeByName(constructor.get(0), "argument");
                for (var i = 0; i < args.size(); i++)
                {
                    List<Node> literalargs = getNodeByName(args.get(i),"literal");
                    List<Node> exprargs = getNodeByName(args.get(i),"expr");
                    if (literalargs.size() > 0 && literalargs.get(0).getAttributes().getNamedItem("type").getNodeValue().equals("number"))
                    {
                        ArgsSig=ArgsSig+ "int";
                    }
                    else if (exprargs.size() > 0)
                    {
                        String expr_var_name = exprargs.get(0).getTextContent();
                        SliceProfile s = null;
                        if (local_variables.containsKey(expr_var_name))
                           s = local_variables.get(expr_var_name).get(expr_var_name);

                        if (global_variables.containsKey(expr_var_name))
                           s = global_variables.get(expr_var_name).get(expr_var_name);

                        if (s == null)
                            return false;
                        
                        ArgsSig=ArgsSig+ s.type_name;
                    }

                    if (i!= args.size() -1)
                        ArgsSig = ArgsSig+",";
                }
            }
            ArgsSig =ArgsSig+')';
        }
        else if (this.a.IsClass(classname))
        {
            // Default constructor
            ArgsSig = "()";
        }
        else if (initializer.size() == 0)
        {
            //Normal primitive datatype
            return true;
        }
        else
        {
            // Normal primitive datatype
            return false;
        }

        profile.setInitSig(ArgsSig);
        if (this.a.IsUninit(classname,ArgsSig))
        {
            this.a.GetUninitField(classname, ArgsSig, profile);

            Iterator it = profile.InnerFields.entrySet().iterator();
            while(it.hasNext())
            {
                Map.Entry pair = (Map.Entry)it.next();
                // System.out.println("The key is: " + pair.getKey() +  "and depth is: " + pair.getValue().toString());
            }
            return true;
        }

        return false;
    }

    private boolean analyze_update(NamePos namePos, Node expr) {
        NamePos expr_var_name_pos_pair = analyzeExpr(expr);
        String expr_var_name = expr_var_name_pos_pair.getName();
//                String expr_var_pos = expr_var_name_pos_pair.getPos();
        if (expr_var_name.equals("")) return true;
        if (local_variables.containsKey(expr_var_name)) {
            updateDVarSliceProfile(namePos.getName(), expr_var_name, "local_variables");
        } else if (global_variables.containsKey(expr_var_name)) {
            updateDVarSliceProfile(namePos.getName(), expr_var_name, "global_variables");
        }
        return false;
    }

    private NamePos analyzeExpr(Node expr_e) {
            String expr_tag = expr_e.getNodeName();
            switch (expr_tag) {
                case "literal":
                    return analyzeLiteralExpr(expr_e);
                case "operator":
                    return analyzeOperatorExpr(expr_e);
                case "ternary":
                    analyzeTernaryExpr(expr_e);
                    break;
                case "call":
                    return analyzeCallExpr(expr_e);
                case "cast":
                    analyzeCastExpr(expr_e);
                    break;
                case "name":
                    return getNamePosTextPair(expr_e);
            }
        return new NamePos("","","",false);
    }

    private NamePos analyzeLiteralExpr(Node literal) {
        String literal_val = literal.getTextContent();
        String type_name = literal.getAttributes().getNamedItem("type").getNodeValue();
        String pos = getNodePos(literal);
        String slice_key = literal_val + "%" + pos + "%" + current_function_name + "%" + file_name;
        SliceProfile profile = new SliceProfile(file_name, current_function_name, literal_val, type_name, pos,current_function_node);
        slice_profiles.put(slice_key,profile);
        Hashtable<String, SliceProfile> lvar = new Hashtable<>();
        lvar.put(literal_val, profile);
        local_variables.put(literal_val,lvar);
        return new NamePos(literal_val,type_name,pos,false);
    }

    private NamePos analyzeOperatorExpr(Node expr) {
//        TODO needs checking
        String text;
        List<Node> specificOp = getNodeByName(expr.getParentNode(), "name");
        if(specificOp.size()<1) text = getNamePosTextPair(expr.getParentNode()).getName();
        else text = specificOp.get(0).getTextContent();
        return new NamePos(text.split(identifier_separator)[0],"",getNodePos(expr),false);
    }

    private void analyzeTryBlock(Node stmt) {
        List<Node> block;
        block = getNodeByName(stmt,"block");
        if(isIndexOutOfBounds(block,0)) return;
        analyzeBlock(block.get(0));
        block = getNodeByName(stmt,"catch");
        if(isIndexOutOfBounds(block,0)) return;
        analyzeCatchBlock(block.get(0));
    }

    private void analyzeCatchBlock(Node catch_block) {
        List<Node> param = getNodeByName(catch_block, "parameter");
        for (Node node : param) {
            analyzeParam(node);
        }
        List<Node> block = getNodeByName(catch_block, "block");
        if(isIndexOutOfBounds(block,0)) return;
        analyzeBlock(block.get(0));
    }

    private void analyzeSwitchStmt(Node stmt) {
        analyzeConditionBlock(stmt);
    }

    private void analyzeCaseStmt(Node stmt) {
        analyzeCompoundExpr(stmt);
    }

    @SuppressWarnings("unused")
    private NamePos analyzeCallExpr(Node call) {
        NamePos cfunction_details = getNamePosTextPair(call);
        String cfunction_name = cfunction_details.getName();
        String cfunction_pos = cfunction_details.getPos();

        String cfunction_identifier = call.getTextContent().split(identifier_separator)[0];
        if(!local_variables.containsKey(cfunction_identifier) || !global_variables.containsKey(cfunction_identifier))
        {
            String cfunction_slice_identifier = cfunction_identifier + "%" + cfunction_pos;
            String cfunc_slice_key = cfunction_slice_identifier + "%" + current_function_name + "%" + file_name;
            SliceProfile cfunction_profile = new SliceProfile(file_name, current_function_name, cfunction_identifier, null, cfunction_pos, current_function_node);
            slice_profiles.put(cfunc_slice_key,cfunction_profile);
            Hashtable<String, SliceProfile> cfprofile = new Hashtable<>();
            cfprofile.put(cfunction_identifier, cfunction_profile);
            local_variables.put(cfunction_identifier,cfprofile);

            List<Node> args = getNodeByName(call, "argument");
            for (Node n: args)
            {
                List<Node> vars = getNodeByName(n,"name");

                if (vars.size()==0)
                continue;

                String varname = vars.get(0).getTextContent().split("-|\\.|>")[0];
                if (local_variables.contains(varname) && !local_variables.get(varname).get(varname).initialized)
                {
                    System.out.println("Uninitialized var "+varname+" called in function! "+ cfunction_name);
                }
            }
        }

        NamePos todo_prevent_return = getNamePos(call, cfunction_name, cfunction_pos, cfunction_identifier);
        return new NamePos(cfunction_identifier,"",cfunction_pos,false);
    }

    private NamePos getNamePos(Node call, String cfunction_name, String cfunction_pos, String cfunction_identifier) {
        List<Node> argument_list = getNodeByName(getNodeByName(call, "argument_list").get(0),"argument");
        int arg_pos_index = 0;
        for(Node arg_expr:argument_list){
         arg_pos_index = arg_pos_index + 1;
            for(Node expr:asList(getNodeByName(arg_expr,"expr").get(0).getChildNodes())){
                NamePos var_name_pos_pair = analyzeExpr(expr);
                String var_name = var_name_pos_pair.getName();
                String var_pos = var_name_pos_pair.getPos();
                String slice_key = var_name + "%" + var_pos + "%" + this.current_function_name + "%" + this.file_name;
                if(var_name.equals("")) return var_name_pos_pair;
                if (local_variables.containsKey(var_name)){
                    updateCFunctionsSliceProfile(var_name, cfunction_name, cfunction_pos,arg_pos_index,"local_variables",slice_key);
                    if(!cfunction_identifier.equals("")) updateDVarSliceProfile(cfunction_identifier, var_name, "local_variables");
                }
                else if(global_variables.containsKey(var_name)){
                    updateCFunctionsSliceProfile(var_name, cfunction_name, cfunction_pos,arg_pos_index,"global_variables",slice_key);
                    if(!cfunction_identifier.equals("")) updateDVarSliceProfile(cfunction_identifier, var_name, "global_variables");
                }
                else if(is_literal_expr(expr)){
                    String type_name = var_name_pos_pair.getType();
                    SliceProfile slice_profile = new SliceProfile(this.file_name, this.current_function_name, var_name, type_name, var_pos,this.current_function_node);
                    cFunction cFun = new cFunction(arg_pos_index,current_function_name ,current_function_node);
                    slice_profile.cfunctions.put(cfunction_name,cFun);
                    slice_profiles.put(slice_key,slice_profile);
                }
            }
        }
        return null;
    }

    private void analyzeCastExpr(Node cast_expr) {
        List<Node> argument_list = getNodeByName(getNodeByName(cast_expr, "argument_list").get(0),"argument");
        for(Node arg_expr:argument_list){
            for(Node expr:asList(getNodeByName(arg_expr,"expr").get(0).getChildNodes())){
                analyzeExpr(expr);
            }
        }
    }

    private void updateCFunctionsSliceProfile(String var_name, String cfunction_name, String cfunction_pos, int arg_pos_index, String slice_variables_string, String slice_key) {
        Hashtable<String, Hashtable<String, SliceProfile>> slice_variables;
        if(slice_variables_string.equals("local_variables")) slice_variables = local_variables;
        else slice_variables = global_variables;
        SliceProfile slice_profile = slice_variables.get(var_name).get(var_name);
        cFunction cFun = new cFunction(arg_pos_index, current_function_name, cfunction_pos,current_function_node);
        slice_profile.cfunctions.put(cfunction_name,cFun);
        slice_profiles.put(slice_key,slice_profile);
        Hashtable<String, SliceProfile> body = slice_variables.get(var_name);
        body.put(var_name, slice_profile);
        if(slice_variables_string.equals("local_variables")) local_variables.put(var_name, body);
        else global_variables.put(var_name, body);
    }

    private void analyzeIfStmt(Node stmt) {
        List<Node> ifA = getNodeByName(stmt,"if");
        for (var i = 0; i< ifA.size() ; i++)
        {
            analyzeIfBlock(ifA.get(i));
        }
        List<Node> elseB = getNodeByName(stmt, "else");
        if(elseB.size()>0)
        analyzeElseBlock(elseB.get(0));
    }

    private void analyzeIfBlock(Node stmt){
        analyzeConditionBlock(stmt);
    }

    private void analyzeConditionBlock(Node stmt) {
        List<Node> condition = getNodeByName(stmt, "condition");
        if(condition.size()>0) 
        {
            analyzeCompoundExpr(condition.get(0));
        }

        List<Node> block = getNodeByName(stmt, "block");
        if(block.size()>0) analyzeBlock(block.get(0));

    }

    private void analyzeReturnStmt(Node stmt) {
        List<Node> expr = getNodeByName(stmt, "expr"); 
        if(expr.size()>0)
        analyzeExpr(expr.get(0).getChildNodes().item(0));
    }

    private void analyzeElseBlock(Node node) {
        donotsavestate = true;
        List<Node> block = getNodeByName(node, "block");
        if(block.size()>0)
        analyzeBlock(block.get(0));
    }

    private void analyzeForStmt(Node stmt) {
        analyzeControl(getNodeByName(stmt,"control").get(0));
        analyzeBlock(getNodeByName(stmt,"block").get(0));
    }

    private void analyzeControl(Node control) {
        List<Node> init = getNodeByName(control, "init");
        if(init.size()>0){
            List<Node> decl = getNodeByName(init.get(0), "decl");
            if(decl.size()>0)
                analyzeDecl(decl);
        }
        List<Node> condition = getNodeByName(control, "condition");
        if(condition.size()>0)
        analyzeConditionExpr(condition.get(0));
        List<Node> incr = getNodeByName(control, "incr");
        if(incr.size()>0)
        analyzeExpr(incr.get(0));
    }

    private void analyzeWhileStmt(Node stmt) {
        analyzeConditionBlock(stmt);
    }

    private void analyzeConditionExpr(Node condition) {
        analyzeCompoundExpr(condition);
    }

    private void analyzeTernaryExpr(Node expr) {
        analyzeConditionExpr(getNodeByName(expr,"condition").get(0));
        analyzeCompoundExpr(getNodeByName(expr,"then").get(0));
        analyzeCompoundExpr(getNodeByName(expr,"else").get(0));
    }

    private void analyzeParam(Node param) {
        analyzeDecl(getNodeByName(param,"decl"));
    }

    private void analyzeExprStmt(Node expr_stmt){
        analyzeCompoundExpr(expr_stmt);
    }

    private void analyzeCompoundExpr(Node init_expr) {
        List<Node> expr1 = getNodeByName(init_expr, "expr");
        if(expr1.size()>0) {
            List<Node> exprs = asList(expr1.get(0).getChildNodes());
            if (is_assignment_expr(exprs)) {
                analyzeAssignmentExpr(exprs);
            } else {
                for (Node expr : exprs) {
                    analyzeExpr(expr);
                }
            }
        }
//      TODO check for pointers and update slice profiles
    }

    private void analyzeAssignmentExpr(List<Node> exprs) {
        Node lhs_expr = exprs.get(0);
        NamePos lhs_expr_name_pos_pair = analyzeExpr(lhs_expr);
        String lhs_expr_var_name = lhs_expr_name_pos_pair.getName();
        String lhs_expr_pos = lhs_expr_name_pos_pair.getPos();

        SliceProfile l_var_profile = null;
            if (local_variables.containsKey(lhs_expr_var_name)){
                l_var_profile = local_variables.get(lhs_expr_var_name).get(lhs_expr_var_name);
            }
            else if(global_variables.containsKey(lhs_expr_var_name)){
                l_var_profile = global_variables.get(lhs_expr_var_name).get(lhs_expr_var_name);
            }

        for (var i=1;i<exprs.size();i++)
        {
            if ((exprs.get(i).getNodeType() == Node.ELEMENT_NODE) && (exprs.get(i).getNodeName() == "name"))
            {
                Node rhs_expr = exprs.get(i);    
                NamePos rhs_expr_name_pos_pair = analyzeExpr(rhs_expr);           
                String rhs_expr_var_name = rhs_expr_name_pos_pair.getName();
                // System.out.println("LHS: "+lhs_expr_var_name+" RHS: "+rhs_expr_var_name+" size: "+exprs.size());
                String rhs_expr_pos = rhs_expr_name_pos_pair.getPos();


                if(lhs_expr_var_name == null || rhs_expr_var_name == null || lhs_expr_var_name.equals(rhs_expr_var_name)) return;

                if (local_variables.containsKey(rhs_expr_var_name)){
                    updateDVarSliceProfile(lhs_expr_var_name,rhs_expr_var_name,"local_variables");
                }
                else if(global_variables.containsKey(rhs_expr_var_name)){
                    updateDVarSliceProfile(lhs_expr_var_name,rhs_expr_var_name,"global_variables");
                }

                boolean is_buffer_write = isBufferWriteExpr(lhs_expr);
                //if(!is_buffer_write) return;


                if (l_var_profile==null) return;

                Tuple buffer_write_pos_tuple = new Tuple(DataAccessType.BUFFER_WRITE, lhs_expr_pos);
                SliceVariableAccess var_access = new SliceVariableAccess();
                var_access.addWrite_positions(buffer_write_pos_tuple);
                l_var_profile.used_positions.add(var_access);
                l_var_profile.setUsed_positions(l_var_profile.used_positions);
            }
        }

        //System.out.println("Variable: "+ l_var_profile.var_name + " written at: "+ lhs_expr_pos);
    }

    private boolean isBufferWriteExpr(Node expr) {
        if(!expr.getNodeName().equals("name")) return false;
        List<Node> comp_temp = getNodeByName(expr, "index");
        if(comp_temp.size()<1) return false;
        Node comp_tag2 = comp_temp.get(0);
        List<Node> comp = getNodeByName(comp_tag2,"expr");
        return comp.size()>0;
    }

    private void updateDVarSliceProfile(String l_var_name, String r_var_name, String slice_variables_string) {
        Hashtable<String, Hashtable<String, SliceProfile>> slice_variables;
        if(slice_variables_string.equals("local_variables")) slice_variables = local_variables;
        else slice_variables = global_variables;

        SliceProfile profile = slice_variables.get(r_var_name).get(r_var_name);
        String l_var_encl_function_name = current_function_name;

        SliceProfile l_var_profile;
        String l_var_defined_pos;
        if (global_variables.containsKey(l_var_name)){
            l_var_encl_function_name = GLOBAL;
            l_var_profile = global_variables.get(l_var_name).get(l_var_name);
        }
        else if(local_variables.containsKey(l_var_name)){
            l_var_profile = local_variables.get(l_var_name).get(l_var_name);
        }
        else return;

        l_var_defined_pos = l_var_profile.defined_position;

        NamePos dvar_pos_pair = new NamePos(l_var_name,l_var_encl_function_name,l_var_defined_pos,false);

        int n = profile.dependent_vars.length;
        NamePos[] arrlist = new NamePos[n+1];
        System.arraycopy(profile.dependent_vars, 0, arrlist, 0, n);
        arrlist[n] = dvar_pos_pair;
        profile.dependent_vars = arrlist;
        Hashtable<String, SliceProfile> body = new Hashtable<>();
        body.put(r_var_name,profile);
        if(slice_variables_string.equals("local_variables")) local_variables.put(r_var_name, body);
        else global_variables.put(r_var_name, body);
    }


    private boolean is_assignment_expr(List<Node> exprs) {
        for (int i=0; i < exprs.size(); i++)
        {
            Node operator_expr = exprs.get(i);
            if (operator_expr.getNodeName().equals("operator") && operator_expr.getFirstChild().getNodeValue().equals("="))
            {
                return true;
            }
        }

        return false;
        //if(exprs.size()!=5) return false;
        //Node operator_expr = exprs.get(2);
        //return operator_expr.getNodeName().equals("operator")&& operator_expr.getFirstChild().getNodeValue().equals("=");
    }

    private boolean is_literal_expr(Node expr) {
        return expr.getFirstChild().getNodeName().equals("literal");
    }
}
