/*
Copyright (c) REBUILD <https://getrebuild.com/> and its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.server.service.base;

import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.engine.ID;
import cn.devezhao.persist4j.record.JsonRecordCreator;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.server.Application;
import com.rebuild.server.helper.datalist.BatchOperatorQuery;
import com.rebuild.server.metadata.EntityHelper;
import com.rebuild.server.service.DataSpecificationException;
import org.apache.commons.lang.StringUtils;

/**
 * 批量修改
 *
 * @author devezhao
 * @since 2019/12/2
 */
public class BulkBacthUpdate extends BulkOperator {

    /**
     * 修改为
     */
    public static final String OP_SET = "SET";
    /**
     * 置空
     */
    public static final String OP_NULL = "NULL";

    protected BulkBacthUpdate(BulkContext context, GeneralEntityService ges) {
        super(context, ges);
    }

    @Override
    protected Integer exec() throws Exception {
        final ID[] willUpdates = prepareRecords();
        this.setTotal(willUpdates.length);

        JSONArray updateContents = context.getCustomData().getJSONArray("updateContents");
        // 转化成标准 FORM 格式
        JSONObject formJson = new JSONObject();
        for (Object o : updateContents) {
            JSONObject item = (JSONObject) o;
            String field = item.getString("field");
            String op = item.getString("op");
            String value = item.getString("value");

            if (OP_NULL.equalsIgnoreCase(op)) {
                formJson.put(field, StringUtils.EMPTY);
            } else if (OP_SET.equalsIgnoreCase(op)) {
                formJson.put(field, value);
            }
        }
        JSONObject metadata = new JSONObject();
        metadata.put("entity", context.getMainEntity().getName());
        formJson.put(JsonRecordCreator.META_FIELD, metadata);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Converter to : " + formJson);
        }

        for (ID id : willUpdates) {
            if (Application.getPrivilegesManager().allowUpdate(context.getOpUser(), id)) {
                // 更新记录
                formJson.getJSONObject(JsonRecordCreator.META_FIELD).put("id", id.toLiteral());

                try {
                    Record record = EntityHelper.parse(formJson, context.getOpUser());
                    ges.update(record);
                    this.addSucceeded();
                } catch (DataSpecificationException ex) {
                    LOG.warn("Couldn't update : " + id + " Ex : " + ex);
                }
            } else {
                LOG.warn("No have privileges to UPDATE : " + context.getOpUser() + " > " + id);
            }
            this.addCompleted();
        }

        return getSucceeded();
    }

    @Override
    protected ID[] prepareRecords() {
        JSONObject customData = context.getCustomData();
        int dataRange = customData.getIntValue("_dataRange");
        BatchOperatorQuery query = new BatchOperatorQuery(dataRange, customData.getJSONObject("queryData"));
        return query.getQueryedRecords();
    }
}
