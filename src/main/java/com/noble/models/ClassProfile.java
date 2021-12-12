package com.noble.models;
import java.util.*;

class ClassManipulation{
    class ClassDef implements Comparable<ClassDef>{
        String name;
        HashMap<String,String> varType = new HashMap<String,String>();
        Set<String> InnerClass = new HashSet<String>();
        HashMap<String,Set<String>> ConstructorSig = new HashMap<String,Set<String>>();
        HashMap<String,Set<String>> ConstructorUninitVars = new HashMap<String,Set<String>>();
        HashMap<String,HashMap<String,String>> InnerConstructorInit = new HashMap<String,HashMap<String,String>>();

        ClassDef(String name)
        {
            this.name = name;
        }

        @Override
        public int compareTo(ClassDef x)
        {
            return this.InnerClass.size() - x.InnerClass.size();
        }
    }
    HashMap<String,ClassDef> Classes= new HashMap<String,ClassDef>();

    void AddClass(String cname)
    {
        if (!Classes.containsKey(cname))
        {
            Classes.put(cname, new ClassDef(cname));
        }

    }

    void AddVars(String cname,String var, String type)
    {
        if (Classes.containsKey(cname))
        {
            Classes.get(cname).varType.put(var,type);
        }

    }

    void AddInnerClass(String cname,String cnameinner)
    {
        if (Classes.containsKey(cname))
        {
            Classes.get(cname).InnerClass.add(cnameinner); 
        }
    }

    void AddConstructorSig(String cname,String Sig)
    {
        Set<String> InitializedVars = new HashSet<String>();
        if (Classes.containsKey(cname))
        {
            Classes.get(cname).ConstructorSig.put(Sig, InitializedVars);
        }
    }

    void AddInitializedVars(String cname, String Sig, Set<String> InitVars)
    {
        if (Classes.containsKey(cname))
        {
            ClassDef c = Classes.get(cname);
            if (c.ConstructorSig.containsKey(Sig))
            {
                c.ConstructorSig.put(Sig, InitVars);
            }
        }
    }

    void AddInnerConstructorInit(String cname, String Sig, HashMap<String,String> InitConstructorInit)
    {
        if (Classes.containsKey(cname))
        {
            ClassDef c = Classes.get(cname);
            if (c.ConstructorSig.containsKey(Sig))
            {
                Iterator it =InitConstructorInit.entrySet().iterator();
                while (it.hasNext())
                {
                    Map.Entry pair = (Map.Entry)it.next();
                    //System.out.println("Insert :"+Sig +" key: "+ pair.getKey()+ " value: "+pair.getValue());

                }
                
                if (InitConstructorInit.size() > 0)
                {
                    c.InnerConstructorInit.put(Sig, InitConstructorInit);
                }
            }
        }
    }

    String IdentifyVarType(String cname,String var)
    {
        if (Classes.containsKey(cname))
        {
            ClassDef c = Classes.get(cname);
            return c.varType.get(var);
        }

        return "";
    }
    

    void FindUninitializedVars()
    {
        // Sorting from Innermost class to Outermost class
        PriorityQueue<ClassDef> minHeap = new PriorityQueue<>();
        Iterator classit = Classes.entrySet().iterator();
        while(classit.hasNext())
        {
            Map.Entry pair = (Map.Entry)classit.next();
            ClassDef c = (ClassDef)pair.getValue();
            minHeap.add(c);
        }

        // Iterating from Innermost class to Outermost class
        while (minHeap.size() > 0)
        {
            ClassDef c = minHeap.poll();
            Iterator sigIterator = c.ConstructorSig.entrySet().iterator();

            // Iterating constructors
            while (sigIterator.hasNext())
            {
                Set<String> UnInitVars =  new HashSet<String>();

                Map.Entry p = (Map.Entry)sigIterator.next();
                String Sig = (String)p.getKey();
                Set<String> InitVars = (Set<String>)p.getValue();
                
                Iterator Fieldit = c.varType.entrySet().iterator();

                // Iterating fields
                while (Fieldit.hasNext())
                {
                    Map.Entry p2 = (Map.Entry)Fieldit.next();
                    String field = (String)p2.getKey();
                    String type = c.varType.get(field);

                    //System.out.println("Tryng for field: " +field + " in sig: "+ Sig);

                    // If field is a InnerClass onject
                    if ( c.InnerClass.contains(type) )
                    {
                        boolean found = false;
                        // If InnerClass object is explicitly initialized with a constructor
                        if (c.InnerConstructorInit.containsKey(Sig))
                        {
                            //System.out.println("Here");
                            HashMap<String,String> InitSig = c.InnerConstructorInit.get(Sig);

                            if (InitSig.containsKey(field))
                            {
                                found = true;
                                String objectSig = InitSig.get(field);
                                if (Classes.get(type).ConstructorUninitVars.containsKey(objectSig) && Classes.get(type).ConstructorUninitVars.get(objectSig).size()>0)
                                {
                                    // System.out.println("Cingo!!!");
                                    UnInitVars.add(field);
                                }
                                
                            }
                        }
                        
                        // If InnerClass object is not explicitly initialized hence using default constructor
                        if (found == false)
                        {
                            if (Classes.get(type).ConstructorUninitVars.containsKey("()") && Classes.get(type).ConstructorUninitVars.get("()").size()>0)
                            {
                                // System.out.println("Tringooo!!!");
                                UnInitVars.add(field);
                            }
                            else if ((!Classes.get(type).ConstructorSig.containsKey("()")) && (Classes.get(type).varType.size() > 0))
                            {
                                // System.out.println("Fringooo!!!");
                                UnInitVars.add(field);
                            }
                        }
                    }
                    else if (!InitVars.contains(field))
                    {
                        // Field is primitive datatype
                        // System.out.println("Kringooo!!!" + field);
                        UnInitVars.add(field);
                    }
                }
                c.ConstructorUninitVars.put(Sig,UnInitVars);
            }

            // Default constructor absent
            if (!Classes.get(c.name).ConstructorSig.containsKey("()"))
            {
                Set<String> UnInitVars = new HashSet<String>();
                HashMap<String,String> InnerDefConstructor = new HashMap<String,String>();
                Iterator Fieldit = Classes.get(c.name).varType.entrySet().iterator();
                while (Fieldit.hasNext())
                {
                    Map.Entry p = (Map.Entry)Fieldit.next();
                    String field = (String)p.getKey();
                    String fieldtype = (String)p.getValue();

                    if (Classes.get(c.name).InnerClass.contains(fieldtype))
                    {
                        InnerDefConstructor.put(field,"()");

                        if (Classes.get(fieldtype).ConstructorUninitVars.get("()").size()>0)
                        {
                            UnInitVars.add(field);
                        }
                    }
                    else
                    {
                        UnInitVars.add(field);
                    }
                        
                }
                c.ConstructorUninitVars.put("()",UnInitVars);
                c.InnerConstructorInit.put("()", InnerDefConstructor);
            }
        }
    }

    void print()
    {
        Iterator classit = Classes.entrySet().iterator();
        while(classit.hasNext())
        {
            Map.Entry pair = (Map.Entry)classit.next();
            ClassDef c = (ClassDef)pair.getValue();
            System.out.println("The name of class : " + c.name );
            

            System.out.print("The fields and their types are: ");
            Iterator Fieldit = c.varType.entrySet().iterator();
            while (Fieldit.hasNext())
            {
                Map.Entry p2 = (Map.Entry)Fieldit.next();
                String field = (String)p2.getKey();
                String type = (String)p2.getValue();
                System.out.println(field + " ---> "+type+" ,");
            }
            System.out.println("The constructor signatures and corresponding uninitialized variables are : ");
            Iterator ConstructorSig = c.ConstructorUninitVars.entrySet().iterator();
            while(ConstructorSig.hasNext())
            {
                Map.Entry p = (Map.Entry)ConstructorSig.next();
                String sig = (String)p.getKey();
                Set<String> InitVars = (Set<String>)p.getValue();
                System.out.print(sig + "--->");

                Iterator InitVarsit = InitVars.iterator();
                System.out.print("(");
                while(InitVarsit.hasNext())
                {
                    System.out.print(InitVarsit.next());
                    System.out.print(",");
                }
                System.out.println(")");
            }
            
            System.out.print("Inner Classes are : ");
            Iterator<String> InnerClass = c.InnerClass.iterator();
            while (InnerClass.hasNext())
            {
                System.out.print(InnerClass.next() + " ,");
            }
            System.out.println("");
            System.out.println("");
            System.out.println("");
        }
    }

    Boolean IsUninit(String classname, String sig)
    {
        if (Classes.containsKey(classname))
        {
            if (Classes.get(classname).ConstructorUninitVars.containsKey(sig))
            {
                return true;
            }
        }
        return false;
    }

}