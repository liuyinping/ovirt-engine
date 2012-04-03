package org.ovirt.engine.ui.webadmin.section.main.view.popup.host.panels;

import org.ovirt.engine.ui.uicommonweb.models.hosts.network.LogicalNetworkModel;

import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.ColumnFormatter;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

public class NetworkPanel extends NetworkItemPanel {

    public NetworkPanel(LogicalNetworkModel item, NetworkPanelsStyle style) {
        super(item, style, true);
        getElement().addClassName(style.networkPanel());
        if (item.isManagement()) {
            getElement().addClassName(style.mgmtNetwork());
        }

    }

    @Override
    protected Widget getContents() {
        LogicalNetworkModel network = (LogicalNetworkModel) item;

        Image mgmtNetworkImage = new Image(resources.mgmtNetwork());
        Image vmImage = new Image(resources.networkVm());
        Image monitorImage = new Image(resources.networkMonitor());

        Grid rowPanel = new Grid(1, 6);
        rowPanel.setCellSpacing(3);
        rowPanel.setWidth("100%");
        rowPanel.setHeight("100%");

        ColumnFormatter columnFormatter = rowPanel.getColumnFormatter();
        columnFormatter.setWidth(0, "5px");
        columnFormatter.setWidth(1, "20px");
        columnFormatter.setWidth(2, "100%");

        infoImage(mgmtNetworkImage, network.isManagement());
        infoImage(monitorImage, network.getEntity().getis_display());
        infoImage(vmImage, false);

        rowPanel.setWidget(0, 0, dragImage);

        ImageResource statusImage = getStatusImage();
        if (statusImage != null) {
            rowPanel.setWidget(0, 1, new Image(statusImage));
        }
        Label titleLabel = new Label(getItemTitle());
        rowPanel.setWidget(0, 2, titleLabel);
        rowPanel.setWidget(0, 3, mgmtNetworkImage);
        rowPanel.setWidget(0, 4, monitorImage);
        rowPanel.setWidget(0, 5, vmImage);
        return rowPanel;
    }

    protected ImageResource getStatusImage() {
        switch (((LogicalNetworkModel) item).getStatus()) {
        case Operational:
            return resources.upImage();
        case NonOperational:
            return resources.downImage();
        default:
            return resources.questionMarkImage();
        }
    }

    @Override
    protected void onAction() {
        // TODO Auto-generated method stub

    }

    private String getItemTitle() {
        LogicalNetworkModel networkModel = (LogicalNetworkModel) item;
        if (networkModel.hasVlan()) {
            return networkModel.getName() + " (vlan " + networkModel.getVlanId() + ")";
        }
        return item.getName();
    }

    private void infoImage(Image image, boolean show) {
        image.setStylePrimaryName(show ? style.networkImageEnabled() : style.networkImageDisabled());
    }

}
