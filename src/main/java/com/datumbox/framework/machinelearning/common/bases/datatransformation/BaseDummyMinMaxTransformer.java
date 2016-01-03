/**
 * Copyright (C) 2013-2016 Vasilis Vryniotis <bbriniotis@datumbox.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datumbox.framework.machinelearning.common.bases.datatransformation;

import com.datumbox.common.dataobjects.AssociativeArray;
import com.datumbox.common.dataobjects.Dataset;
import com.datumbox.common.dataobjects.FlatDataList;
import com.datumbox.common.dataobjects.Record;
import com.datumbox.common.persistentstorage.interfaces.DatabaseConnector;
import com.datumbox.common.persistentstorage.interfaces.BigMap;
import com.datumbox.common.persistentstorage.interfaces.DatabaseConfiguration;
import com.datumbox.common.dataobjects.TypeInference;
import com.datumbox.framework.statistics.descriptivestatistics.Descriptives;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


/**
 * Base class for Dummy and MinMax Transformers.
 *
 * @author Vasilis Vryniotis <bbriniotis@datumbox.com>
 */
public abstract class BaseDummyMinMaxTransformer extends DataTransformer<BaseDummyMinMaxTransformer.ModelParameters, BaseDummyMinMaxTransformer.TrainingParameters> {
    
    /**
     * Base class for the Model Parameters of the algorithm.
     */
    public static class ModelParameters extends DataTransformer.ModelParameters {
        /**
         * The reference levels of each categorical variable.
         */
        @BigMap
        private Map<Object, Object> referenceLevels;
        
        /**
         * The minimum value of each numerical variable.
         */
        @BigMap
        private Map<Object, Double> minColumnValues;
        
        /**
         * The maximum value of each numerical variable.
         */
        @BigMap
        private Map<Object, Double> maxColumnValues;

        /**
         * Protected constructor which accepts as argument the DatabaseConnector.
         * 
         * @param dbc 
         */
        protected ModelParameters(DatabaseConnector dbc) {
            super(dbc);
        }

        /**
         * Getter for the reference levels of the categorical variables.
         * 
         * @return 
         */
        public Map<Object, Object> getReferenceLevels() {
            return referenceLevels;
        }
        
        /**
         * Setter for the reference levels of the categorical variables.
         * 
         * @param referenceLevels 
         */
        protected void setReferenceLevels(Map<Object, Object> referenceLevels) {
            this.referenceLevels = referenceLevels;
        }
        
        /**
         * Getter for the minimum values of the columns.
         * 
         * @return 
         */
        public Map<Object, Double> getMinColumnValues() {
            return minColumnValues;
        }
        
        /**
         * Setter for the minimum values of the columns.
         * 
         * @param minColumnValues 
         */
        protected void setMinColumnValues(Map<Object, Double> minColumnValues) {
            this.minColumnValues = minColumnValues;
        }
        
        /**
         * Getter for the maximum values of the columns.
         * 
         * @return 
         */
        public Map<Object, Double> getMaxColumnValues() {
            return maxColumnValues;
        }

        /**
         * Setter for the maximum values of the columns.
         * 
         * @param maxColumnValues 
         */
        protected void setMaxColumnValues(Map<Object, Double> maxColumnValues) {
            this.maxColumnValues = maxColumnValues;
        }
        
    }
    
    /**
     * Base class for the Training Parameters of the algorithm.
     */
    public static class TrainingParameters extends DataTransformer.TrainingParameters {
        
    }
    
    /**
     * Protected constructor of the algorithm.
     * 
     * @param dbName
     * @param dbConf 
     */
    protected BaseDummyMinMaxTransformer(String dbName, DatabaseConfiguration dbConf) {
        super(dbName, dbConf, BaseDummyMinMaxTransformer.ModelParameters.class, BaseDummyMinMaxTransformer.TrainingParameters.class);
    }
    
    /**
     * Learns the normalization parameters for the X data.
     * 
     * @param data
     * @param minColumnValues
     * @param maxColumnValues 
     */
    protected static void fitX(Dataset data, Map<Object, Double> minColumnValues, Map<Object, Double> maxColumnValues) {
        
        for(Map.Entry<Object, TypeInference.DataType> entry : data.getXDataTypes().entrySet()) {
            Object column = entry.getKey();
            TypeInference.DataType columnType = entry.getValue();

            if(columnType==TypeInference.DataType.NUMERICAL) {
                FlatDataList columnValues = data.getXColumn(column);
                Double max = Descriptives.max(columnValues.toFlatDataCollection());
                Double min = Descriptives.min(columnValues.toFlatDataCollection());

                minColumnValues.put(column, min);
                maxColumnValues.put(column, max);
            }
            else {
                //do nothing for non-numeric columns
            }
        }

        //do nothing for the response variable Y
    }
    
    /**
     * Normalizes the X data.
     * 
     * @param data
     * @param minColumnValues
     * @param maxColumnValues 
     */
    protected static void normalizeX(Dataset data, Map<Object, Double> minColumnValues, Map<Object, Double> maxColumnValues) {
        for(Integer rId : data.index()) {
            Record r = data.get(rId);
            AssociativeArray xData = r.getX().copy();
            
            boolean modified = false;
            for(Object column : minColumnValues.keySet()) {
                Double value = xData.getDouble(column);
                if(value==null) { //if we have a missing value don't perform any normalization
                    continue;
                }
                
                Double min = minColumnValues.get(column);
                Double max = maxColumnValues.get(column);
                
                //it is important how we will handle 0 normalized values because
                //0-valued features are considered inactive.
                double normalizedValue;
                if(min.equals(max)) {
                    normalizedValue = (min>0.0)?1.0:0.0; //set it 0.0 ONLY if the feature is always inactive and 1.0 if it has a non-zero value
                }
                else {
                    normalizedValue = (value-min)/(max-min);
                }
                
                xData.put(column, normalizedValue);
                modified = true;
            }
            
            if(modified) {
                r = new Record(xData, r.getY(), r.getYPredicted(), r.getYPredictedProbabilities());
                data.set(rId, r);
            }
        }
    }
    
    /**
     * Denormalizes the X data.
     * 
     * @param data
     * @param minColumnValues
     * @param maxColumnValues 
     */
    protected static void denormalizeX(Dataset data, Map<Object, Double> minColumnValues, Map<Object, Double> maxColumnValues) {
        for(Integer rId : data.index()) {
            Record r = data.get(rId);
            AssociativeArray xData = r.getX().copy();
            
            boolean modified = false;
            for(Object column : minColumnValues.keySet()) {
                Double value = xData.getDouble(column);
                if(value==null) { //if we have a missing value don't perform any denormalization
                    continue;
                }
                
                Double min = minColumnValues.get(column);
                Double max = maxColumnValues.get(column);
                
                if(min.equals(max)) {
                    xData.put(column, min);
                }
                else {
                    xData.put(column, value*(max-min) + min);
                }
                modified = true;
            }
            
            if(modified) {
                r = new Record(xData, r.getY(), r.getYPredicted(), r.getYPredictedProbabilities());
                data.set(rId, r);
            }
        }
    }
    
    /**
     * Learns the normalization parameters for the Y variable.
     * 
     * @param data
     * @param minColumnValues
     * @param maxColumnValues 
     */
    protected static void fitY(Dataset data, Map<Object, Double> minColumnValues, Map<Object, Double> maxColumnValues) {
        if(data.getYDataType()==TypeInference.DataType.NUMERICAL) {
            //if this is numeric normalize it

            FlatDataList columnValues = data.getYColumn();
            Double max = Descriptives.max(columnValues.toFlatDataCollection());
            Double min = Descriptives.min(columnValues.toFlatDataCollection());

            minColumnValues.put(Dataset.yColumnName, min);
            maxColumnValues.put(Dataset.yColumnName, max);
        }
    }
    
    /**
     * Normalizes the Y variable.
     * 
     * @param data
     * @param minColumnValues
     * @param maxColumnValues 
     */
    protected static void normalizeY(Dataset data, Map<Object, Double> minColumnValues, Map<Object, Double> maxColumnValues) {
        if(data.isEmpty()) {
            return;
        }
        
        if(data.getYDataType()==TypeInference.DataType.NUMERICAL) {
            
            for(Integer rId : data.index()) {
                Record r = data.get(rId);
                Double value = TypeInference.toDouble(r.getY());
                if(value==null) { //if we have a missing value don't perform any normalization
                    continue;
                }
                
                //do the same for the response variable Y
                Double min = minColumnValues.get(Dataset.yColumnName);
                Double max = maxColumnValues.get(Dataset.yColumnName);
                
                //it is important how we will handle 0 normalized values because
                //0-valued features are considered inactive.
                double normalizedValue;
                if(min.equals(max)) {
                    normalizedValue = (min!=0.0)?1.0:0.0; //set it 0.0 ONLY if the feature is always inactive and 1.0 if it has a non-zero value
                }
                else {
                    normalizedValue = (value-min)/(max-min);
                }
                
                data.set(rId, new Record(r.getX(), normalizedValue, r.getYPredicted(), r.getYPredictedProbabilities()));
            }
        }
    }
    
    /**
     * Denormalizes the Y variable.
     * 
     * @param data
     * @param minColumnValues
     * @param maxColumnValues 
     */
    protected static void denormalizeY(Dataset data, Map<Object, Double> minColumnValues, Map<Object, Double> maxColumnValues) {
        
        if(data.isEmpty()) {
            return;
        }
        
        TypeInference.DataType dataType = data.getYDataType();
        if(dataType==TypeInference.DataType.NUMERICAL || dataType==null) {
            
            for(Integer rId : data.index()) {
                Record r = data.get(rId);
                
                //do the same for the response variable Y
                Double min = minColumnValues.get(Dataset.yColumnName);
                Double max = maxColumnValues.get(Dataset.yColumnName);
                
                Object denormalizedY = null;
                Object denormalizedYPredicted = null;
                if(min.equals(max)) {
                    if(r.getY()!=null) {
                        denormalizedY = min;
                    }
                    if(r.getYPredicted()!=null) {
                        denormalizedYPredicted = min;
                    }
                }
                else {
                    if(r.getY()!=null) {
                        denormalizedY = TypeInference.toDouble(r.getY())*(max-min) + min;
                    }
                    
                    Double YPredicted = TypeInference.toDouble(r.getYPredicted());
                    if(YPredicted!=null) {
                        denormalizedYPredicted = YPredicted*(max-min) + min;
                    }
                }
                
                data.set(rId, new Record(r.getX(), denormalizedY, denormalizedYPredicted, r.getYPredictedProbabilities()));
            }
        }
    }
    
    /**
     * Learns the reference levels of the categorical variables.
     * 
     * @param data
     * @param referenceLevels 
     */
    protected static void fitDummy(Dataset data, Map<Object, Object> referenceLevels) {
        Map<Object, TypeInference.DataType> columnTypes = data.getXDataTypes();

        //find the referenceLevels for each categorical variable
        for(Record r : data) {
            for(Map.Entry<Object, Object> entry: r.getX().entrySet()) {
                Object column = entry.getKey();
                if(referenceLevels.containsKey(column)==false) { //already set?
                    if(covert2dummy(columnTypes.get(column))==false) { 
                        continue; //only ordinal and categorical are converted into dummyvars
                    }
                    Object value = entry.getValue();
                    referenceLevels.put(column, value);
                }
            }
        }
    }
    
    /**
     * Transforms the categorical variables into dummy (boolean) variables.
     * 
     * @param data
     * @param referenceLevels 
     */
    protected static void transformDummy(Dataset data, Map<Object, Object> referenceLevels) {

        Map<Object, TypeInference.DataType> columnTypes = data.getXDataTypes();
        
        //Replace variables with dummy versions
        for(Integer rId: data.index()) {
            Record r = data.get(rId);
            
            AssociativeArray xData = r.getX().copy();
            
            boolean modified = false;
            for(Object column : r.getX().keySet()) {
                if(covert2dummy(columnTypes.get(column))==false) { 
                    continue;
                }
                Object value = xData.get(column);
                
                xData.remove(column); //remove the original column
                modified = true;
                
                Object referenceLevel= referenceLevels.get(column);
                
                if(referenceLevel != null && //not unknown variable
                   !referenceLevel.equals(value)) { //not equal to reference level
                    
                    //create a new column
                    List<Object> newColumn = Arrays.<Object>asList(column,value);
                    
                    //add a new dummy variable for this column-value combination
                    xData.put(newColumn, true); 
                }
            }
            
            if(modified) {
                r = new Record(xData, r.getY(), r.getYPredicted(), r.getYPredictedProbabilities());
                data._set(rId, r);
            }
        }
        
        //Reset Meta info
        data.recalculateMeta();
    }
    
    /**
     * Checks whether the variable should be converted into dummy (boolean).
     * 
     * @param columnType
     * @return 
     */
    private static boolean covert2dummy(TypeInference.DataType columnType) {
        return columnType==TypeInference.DataType.CATEGORICAL || columnType==TypeInference.DataType.ORDINAL;
    }
    
}
