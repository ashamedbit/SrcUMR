package com.noble.models;

import org.w3c.dom.Node;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Set;

public class SliceProfile implements Serializable{
    public String file_name;
    public String function_name;
    public String var_name;
    public String type_name;
    public String defined_position;
    public Boolean initialized = true;
    public Boolean isUMRSink = false;
    public Set <SliceProfile> DepUninitSliceProfiles = new HashSet<SliceProfile>();
    public String InitSig = "";
    public int RetPos;
    public HashMap<String,Integer> InnerFields = new HashMap<String,Integer>();
    public HashMap<String,String> InnerFieldType = new HashMap<String,String>();
    public ArrayList<SliceVariableAccess> used_positions = new ArrayList<>();
    public NamePos[] dependent_vars = new NamePos[]{};
    public Hashtable<String, cFunction> cfunctions = new Hashtable<>();
    public Node function_node;

    String[] pointers = new String[]{};

    public void setUsed_positions(ArrayList<SliceVariableAccess> used_positions) {
        this.used_positions = used_positions;
    }

    public void setUninitialized()
    {
        this.initialized = false;
    }

    public void setInitialized()
    {
        this.initialized = true;
    }

    public void setInitSig(String x)
    {
        this.InitSig = x;
    }

    @SuppressWarnings("unused")
    public SliceProfile(String file_name, String function_name, String var_name, String type_name, String defined_position, ArrayList<SliceVariableAccess> used_positions,
                        NamePos[] dependent_vars, String[] pointers, Hashtable<String, cFunction> cfunctions, Node function_node) {
        this.file_name = file_name;
        this.function_name = function_name;
        this.var_name = var_name;
        this.type_name = type_name;
        this.defined_position = defined_position;
        this.used_positions = used_positions;
        this.dependent_vars = dependent_vars;
        this.pointers = pointers;
        this.cfunctions = cfunctions;
        this.function_node = function_node;
        this.initialized = true;
        this.isUMRSink = false;
        this.RetPos = -1;
    }


    public SliceProfile(String file_name, String function_name, String var_name, String type_name, String defined_position) {
        this.file_name = file_name;
        this.function_name = function_name;
        this.var_name = var_name;
        this.type_name = type_name;
        this.defined_position = defined_position;
        this.initialized = true;
        this.RetPos = -1;
    }

    public SliceProfile(String file_name, String current_function_name, String name, String type, String pos, Node current_function_node) {
        this.file_name = file_name;
        this.function_name = current_function_name;
        this.var_name = name;
        this.type_name = type;
        this.defined_position = pos;
        this.function_node = current_function_node;
        this.initialized = true;
        this.RetPos = -1;
    }
}
