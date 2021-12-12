package com.noble.models;

import com.noble.models.*;
import static com.noble.util.XmlUtil.*;
import java.io.File;
import java.io.IOException;

import javax.lang.model.util.ElementScanner6;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException; 
import org.xml.sax.SAXParseException;

import java.io.PrintWriter;
import java.util.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DetectUMR {

 
    static ClassManipulation m = new ClassManipulation();
    static Set<String> InitVars = new HashSet<String>();
    static HashMap<String,String> InnerConstructorInit = new HashMap<String,String>();
    static HashMap<String,String> ParameterType = new HashMap<String,String>();
    static String Sig;

    static void RecurseConstructor(Node root, String cname)
    {
        NodeList classstruct = root.getChildNodes();
        
        for (int i=0;i<classstruct.getLength();i++)
        {
            Node n = classstruct.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE)
            {
                if ((root.getNodeName() == "decl") && (n.getNodeName() == "name"))
                {
                    String varname = n.getChildNodes().item(0).getTextContent();
                    
                    //m.AddVars(classname, varname);
                    //System.out.println("Initialized in constructor  " + varname);
                }
                else if ((n.getNodeName() == "argument_list") || (n.getNodeName() == "parameter_list"))
                {
                    NodeList args = n.getChildNodes();
                    
                    String argsstring = "";

                    for (int j=1; j<args.getLength()-1;j++)
                    {
                        if(args.item(j).getTextContent().split(" ").length<=1)
                            continue;

                        String type = args.item(j).getTextContent().split(" ")[0];
                        argsstring = argsstring + type;

                        if (type.equals(",")){ continue; }
                        String var = args.item(j).getTextContent().split(" ")[1];
                        ParameterType.put(var, type);
                            
                    }
                    Sig = "(" + argsstring.replace("(","").replace(")","").replace(" ","")+ ")";

                    m.AddConstructorSig(cname, Sig);
                }
                else if (n.getNodeName() == "expr")
                {
                    String varname = n.getChildNodes().item(0).getChildNodes().item(0).getTextContent();
                    //System.out.println("This variable is set " + varname);
                    InitVars.add(varname);
                }
                else
                {
                    RecurseConstructor(n,cname);
                }
            }
        }
    }

    static void InnerClassConstructor(Node root, String cname)
    {
        NodeList classstruct = root.getChildNodes();
        String ClassType = "";
        String ClassObjName = "";
        for (int i=0;i<classstruct.getLength();i++)
        {
            Node n = classstruct.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE)
            {
                if (n.getNodeName() == "name")
                {
                    ClassObjName = n.getChildNodes().item(0).getTextContent();
                    ClassType = m.IdentifyVarType(cname, ClassObjName);
                }
                else if ((n.getNodeName() == "argument_list") || (n.getNodeName() == "parameter_list"))
                {
                    NodeList args = n.getChildNodes();
                    String InitSig = "(";
                    for (int j=1; j<args.getLength()-1;j++)
                    {
                            if (args.item(i).getChildNodes().item(0).getNodeName().equals("literal"))
                            {
                                Node literal = args.item(i).getChildNodes().item(0);
                                if (literal.getAttributes().getNamedItem("type").getNodeValue().equals("number"))
                                {
                                    InitSig = InitSig + "int";
                                }
                            }
                            else
                            {
                                String arg = args.item(j).getTextContent().split(" ")[0].replace(" ","");
                                String type = ParameterType.get(arg);
                                //System.out.println("Inner Constructor Class: "+ ClassType + "  Parameter: " + arg + " type: " + type);
                                InitSig = InitSig + type;
                            }

                            if (j!=args.getLength() -2)
                            {
                                InitSig = InitSig+",";
                            }  
                    }
                    InitSig=InitSig+")";
                    // System.out.println("Inner Constructor Signature: " + InitSig);
                    InnerConstructorInit.put(ClassObjName,InitSig);
                    //String InnerConstructorSig = argsstring.replace("(","").replace(")","").replace(" ","");
                }
            }
        }
    }

    static public void RecurseClass(Node root, String cname)
    {
        NodeList classstruct = root.getChildNodes();
        String classname = cname;
       
        for (int i=0;i<classstruct.getLength();i++)
        {
            Node n = classstruct.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE)
            {
                if ((root.getNodeName() == "class" || root.getNodeName() == "struct") && (n.getNodeName() == "name"))
                {
                    String name = n.getChildNodes().item(0).getTextContent();
                    m.AddInnerClass(classname, name);
                    classname = name;
                    m.AddClass(classname);
                    //System.out.println(name);
                }
                else if(n.getNodeName() == "macro")
                {
                    InitVars = new HashSet<String>();
                    Sig = "";
                    ParameterType = new HashMap<String,String>();
                    InnerConstructorInit = new HashMap<String,String>();

                    int j=i;

                    while (j<classstruct.getLength())
                    {
                        if (classstruct.item(j).getNodeType() != Node.ELEMENT_NODE)
                        {
                            j++;
                            continue;
                        }
                        if (classstruct.item(j).getNodeName() == "block")
                        {
                            RecurseConstructor(classstruct.item(j),classname);
                            break;
                        }

                        if (classstruct.item(j).getNodeName() == "macro")
                        {
                            if (i == j)
                            {
                                // Node is a constructor
                                RecurseConstructor(classstruct.item(j),classname);
                            }
                            else
                            {
                                // Second macro is a constructor for inner classes
                                InnerClassConstructor(classstruct.item(j), classname);        
                            }
                        }
                        j++;
                    }
                    i = j;
                    
                    m.AddInitializedVars(cname, Sig, InitVars);
                    m.AddInnerConstructorInit(cname, Sig, InnerConstructorInit);
                }
                else if(n.getNodeName() == "constructor")
                {
                    InitVars = new HashSet<String>();
                    Sig = "";
                    ParameterType = new HashMap<String,String>();
                    InnerConstructorInit = new HashMap<String,String>();
                    RecurseConstructor(classstruct.item(i),classname);
                    m.AddInitializedVars(cname, Sig, InitVars);
                }
                else if (n.getNodeName() == "decl_stmt")
                {
                    NodeList declstmt = n.getChildNodes();
                    String type = "";
                    for (int k=0; k<declstmt.getLength();k++)
                    {
                        Node fieldnode = declstmt.item(k);
                        if ((fieldnode.getNodeType() == Node.ELEMENT_NODE) && (fieldnode.getNodeName() == "decl"))
                        {
                            String varname = "";
                            
                            // Check name of field, if field is initialized and the type of field
                            NodeList subchildren = fieldnode.getChildNodes();
                            Boolean initialized = false;
                            for (int j=0;j<subchildren.getLength();j++)
                            {
                                Node next = subchildren.item(j);
                                if ((next != null) && (next.getNodeType() == Node.ELEMENT_NODE) && (next.getNodeName() == "name"))
                                {
                                    if (next.getChildNodes().getLength()>0)
                                        varname = next.getChildNodes().item(0).getTextContent();
                                }
                                else if ((next != null) && (next.getNodeType() == Node.ELEMENT_NODE) && (next.getNodeName() == "init"))
                                {
                                    initialized = true;
                                }
                                else if ((next != null) && (next.getNodeType() == Node.ELEMENT_NODE) && (next.getNodeName() == "type"))
                                {
                                    if (next.getChildNodes().getLength()>0 && next.getChildNodes().item(0).getNodeName() == "name")
                                    {
                                        type = next.getChildNodes().item(0).getChildNodes().item(0).getTextContent();
                                        //System.out.println("Varname is: "+ varname + " type: "+ next.getChildNodes().item(0).getChildNodes().item(0).getTextContent() );
                                    }
                                }
                            }

                            if (initialized == false)
                            {
                                m.AddVars(classname, varname, type);   
                            }
                            //System.out.print(varname);
                        }
                    }
                }
                else
                {
                    RecurseClass(n,classname);
                }
            }
        }
    }

    public void printData()
    {
        m.print();
    }

    public void FindUninitialisedConstructors()
    {
        m.FindUninitializedVars();
    }
    public Boolean IsUninit(String classname, String sig)
    {
        classname = classname.replace("*", "");
        if (m.Classes.containsKey(classname))
        {
            if (m.Classes.get(classname).ConstructorUninitVars.containsKey(sig))
            {
                return true;
            }
        }
        return false;
    }

    public Boolean CheckUninit(SliceProfile s, String InitSig)
    {
        HashMap<String,Integer> c = s.InnerFields;
        String[] tokens =  InitSig.split("[-.>]+");

        for (var i=1;i<tokens.length;i++)
        {
            if (c.containsKey(tokens[i].replace("*","")))
            {
                if (c.get(tokens[i].replace("*","")) != i)
                {
                    return false;
                }
            }
            else
            {
                return false;
            }
        }
    
        return true;
    }

    public void RemoveInnerField(SliceProfile s,String Fields)
    {
        HashMap<String,Integer> c = s.InnerFields;
        String[] tokens =  Fields.split("[-.>]+");
        int len = tokens.length;

        if (c.containsKey(tokens[len-1]))
        {
            if (c.get(tokens[len-1]) == len - 1)
            {
                c.remove(tokens[len-1]);
            }
        }
        return;
    }

    public void RecurseFields (SliceProfile s, String signature, String type, int depth)
    {
        Set<String> Fields = m.Classes.get(type).ConstructorUninitVars.get(signature);
        Iterator<String> it = Fields.iterator();

        while (it.hasNext())
        {
            String Field = it.next();
            if (m.Classes.get(type).InnerConstructorInit.containsKey(signature))
            {
                if (m.Classes.get(type).InnerConstructorInit.get(signature).containsKey(Field))
                {
                    String signaturenew = m.Classes.get(type).InnerConstructorInit.get(signature).get(Field);
                    String typenew = m.Classes.get(type).varType.get(Field);
                    s.InnerFieldType.put(Field, typenew);
                    RecurseFields(s, signaturenew, typenew, depth+1);
                }
                s.InnerFields.put(Field, depth);
            }
        }
    }

    public void GetUninitField(String classname, String sig, SliceProfile s)
    {
        classname = classname.replace("*", "");
        if (m.Classes.containsKey(classname))
        {
            if (m.Classes.get(classname).ConstructorUninitVars.containsKey(sig))
            {   
                RecurseFields(s, sig, classname, 1);
            }
        }
        return;
    }

    public Boolean IsClass(String type)
    {
        type = type.replace("*","");
        if (m.Classes.containsKey(type))
        {
            return true;
        }
        return false;
    }
}