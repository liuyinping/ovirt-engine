package org.ovirt.engine.core.bll;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.ovirt.engine.core.bll.validator.StorageDomainValidator;
import org.ovirt.engine.core.common.AuditLogType;
import org.ovirt.engine.core.common.action.AttachDetachVmDiskParameters;
import org.ovirt.engine.core.common.action.ImportVmParameters;
import org.ovirt.engine.core.common.action.VdcActionType;
import org.ovirt.engine.core.common.action.VdcReturnValueBase;
import org.ovirt.engine.core.common.businessentities.Disk;
import org.ovirt.engine.core.common.businessentities.OvfEntityData;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.errors.VdcBllMessages;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dal.dbbroker.auditloghandling.AuditLogDirector;
import org.ovirt.engine.core.utils.log.Log;
import org.ovirt.engine.core.utils.log.LogFactory;
import org.ovirt.engine.core.utils.ovf.OvfReaderException;

@LockIdNameAttribute
@NonTransactiveCommandAttribute(forceCompensation = true)
public class ImportVmFromConfigurationCommand<T extends ImportVmParameters> extends ImportVmCommand<T> {

    private static final Log log = LogFactory.getLog(ImportVmFromConfigurationCommand.class);
    private Collection<Disk> vmDisksToAttach;
    private OvfEntityData ovfEntityData;
    VM vmFromConfiguration;

    protected ImportVmFromConfigurationCommand(Guid commandId) {
        super(commandId);
    }

    public ImportVmFromConfigurationCommand(T parameters) {
        super(parameters);
        setCommandShouldBeLogged(false);
    }

    @Override
    protected boolean canDoAction() {
        if (isImagesAlreadyOnTarget()) {
            if (ovfEntityData == null && !getParameters().isImportAsNewEntity()) {
                addCanDoActionMessage(VdcBllMessages.ACTION_TYPE_FAILED_UNSUPPORTED_OVF);
                return false;
            }
            if (vmFromConfiguration == null) {
                addCanDoActionMessage(VdcBllMessages.ACTION_TYPE_FAILED_OVF_CONFIGURATION_NOT_SUPPORTED);
                return false;
            }
            setStorageDomainId(ovfEntityData.getStorageDomainId());
            if (!validate(new StorageDomainValidator(getStorageDomain()).isDomainExistAndActive())) {
                return false;
            }
            setImagesWithStoragePoolId(getStorageDomain().getStoragePoolId(), getVm().getImages());
            if (!getStorageDomain().getStorageDomainType().isDataDomain()) {
                return failCanDoAction(VdcBllMessages.ACTION_TYPE_FAILED_STORAGE_DOMAIN_TYPE_UNSUPPORTED,
                        String.format("$domainId %1$s", getParameters().getStorageDomainId()),
                        String.format("$domainType %1$s", getStorageDomain().getStorageDomainType()));
            }
        }
        return super.canDoAction();
    }

    @Override
    protected void init(T parameters) {
        VM vmFromConfiguration = getParameters().getVm();
        if (vmFromConfiguration != null) {
            vmFromConfiguration.getStaticData().setVdsGroupId(getParameters().getVdsGroupId());
            if (!isImagesAlreadyOnTarget()) {
                setDisksToBeAttached(vmFromConfiguration);
            }
            getParameters().setContainerId(vmFromConfiguration.getId());
        } else {
            initUnregisteredVM();
        }

        setVdsGroupId(getParameters().getVdsGroupId());
        getParameters().setStoragePoolId(getVdsGroup().getStoragePoolId());
        super.init(parameters);
    }

    private void initUnregisteredVM() {
        OvfHelper ovfHelper = new OvfHelper();
        ovfEntityData =
                getUnregisteredOVFDataDao().getByEntityIdAndStorageDomain(getParameters().getContainerId(),
                        getParameters().getStorageDomainId());
        if (ovfEntityData != null) {
            try {
                vmFromConfiguration = ovfHelper.readVmFromOvf(ovfEntityData.getOvfData());
                vmFromConfiguration.setVdsGroupId(getParameters().getVdsGroupId());
                getParameters().setVm(vmFromConfiguration);
                getParameters().setDestDomainId(ovfEntityData.getStorageDomainId());
                getParameters().setSourceDomainId(ovfEntityData.getStorageDomainId());
            } catch (OvfReaderException e) {
                log.errorFormat("failed to parse a given ovf configuration: \n" + ovfEntityData.getOvfData(), e);
            }
        }
    }

    private void setDisksToBeAttached(VM vmFromConfiguration) {
        vmDisksToAttach = vmFromConfiguration.getDiskMap().values();
        clearVmDisks(vmFromConfiguration);
        getParameters().setCopyCollapse(true);
    }

    @Override
    public void executeCommand() {
        super.executeCommand();
        if (getSucceeded()) {
            if (isImagesAlreadyOnTarget()) {
                getUnregisteredOVFDataDao().removeEntity(ovfEntityData.getEntityId(), ovfEntityData.getStorageDomainId());
            } else if (!vmDisksToAttach.isEmpty()) {
                AuditLogDirector.log(this, attemptToAttachDisksToImportedVm(vmDisksToAttach));
            }
        }
        setActionReturnValue(getVm().getId());
    }

    private void clearVmDisks(VM vm) {
        vm.setDiskMap(Collections.<Guid, Disk> emptyMap());
        vm.getImages().clear();
        vm.getDiskList().clear();
    }

    private AuditLogType attemptToAttachDisksToImportedVm(Collection<Disk> disks){
        List<String> failedDisks = new LinkedList<>();
        for (Disk disk : disks) {
            AttachDetachVmDiskParameters params = new AttachDetachVmDiskParameters(getVm().getId(),
                    disk.getId(), disk.getPlugged(), disk.getReadOnly());
            VdcReturnValueBase returnVal = getBackend().runInternalAction(VdcActionType.AttachDiskToVm, params);
            if (!returnVal.getSucceeded()) {
                failedDisks.add(disk.getDiskAlias());
            }
        }

        if (!failedDisks.isEmpty()) {
            this.addCustomValue("DiskAliases", StringUtils.join(failedDisks, ","));
            return AuditLogType.VM_IMPORT_FROM_CONFIGURATION_ATTACH_DISKS_FAILED;
        }

        return AuditLogType.VM_IMPORT_FROM_CONFIGURATION_EXECUTED_SUCCESSFULLY;
    }

}
