package oram.client.structure;

public class OngoingAccessContext {
	private boolean isRealAccess;

	public OngoingAccessContext() {
		this.isRealAccess = true;
	}

	public void setIsRealAccess(boolean isRealAccess) {
		this.isRealAccess = isRealAccess;
	}

	public boolean isRealAccess() {
		return isRealAccess;
	}

}
