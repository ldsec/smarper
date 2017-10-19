function [post nlZ dnlZ] = infFITC_prox(hyp, mean, cov, lik, x, y)
%batch KL proximal gradient for sparse GP

persistent last_alpha                                   % copy of the last alpha
if any(isnan(last_alpha)), last_alpha = zeros(size(last_alpha)); end   % prevent

inf = 'infLaplace';

cov1 = cov{1}; if isa(cov1, 'function_handle'), cov1 = func2str(cov1); end
if ~strcmp(cov1,'covFITC'); error('Only covFITC supported.'), end    % check cov
if isfield(hyp,'xu'), cov{3} = hyp.xu; end  % hyp.xu is provided, replace cov{3}

[diagK,Kuu,Ku] = feval(cov{:}, hyp.cov, x);         % evaluate covariance matrix
if ~isempty(hyp.lik)                          % hard coded inducing inputs noise
  sn2 = exp(2*hyp.lik(end)); snu2 = 1e-6*sn2;               % similar to infFITC
else
  snu2 = 1e-6;
end
[n, D] = size(x); nu = size(Kuu,1);
m = feval(mean{:}, hyp.mean, x);                      % evaluate the mean vector

Kuu=Kuu+snu2*eye(nu);
R = chol(Kuu);           % initial R, used for refresh O(nu^3)
%R'*R = K_m

lik_name = func2str(lik{1});
rot180   = @(A)   rot90(rot90(A));                     % little helper functions
chol_inv = @(A) rot180(chol(rot180(A))')\eye(nu);                 % chol(inv(A))
R0 = chol_inv(Kuu);           % initial R, used for refresh O(nu^3)
V = R0*Ku; d0 = diagK-sum(V.*V,1)';    % initial d, needed for refresh O(n*nu^2)
const_term=-0.5*nu+sum(log(diag(R)));

nlZ=Inf;
m_0 = feval(mean{:}, hyp.mean, cov{3});                      % evaluate the mean vector

m_u = m_0;
V_u = Kuu;

it = 0;    

H=R0'*V;


rate=hyp.learning_rate;
maxit = hyp.max_pass;                                    % max number of steps in f
lambda=zeros(n,1);
while it<maxit
	post_m=m+H'*(m_u-m_0);
	C=chol(V_u,'lower');
	tmp1=C'*R0';
	V1=tmp1*V;
	post_v=d0+sum(V1.*V1,1)';
	if hyp.stochastic_approx==1
		[ll, df, dv] = sampling_E(y, post_m, post_v, lik, hyp.sample_size, hyp.lik);
	else
		switch lik_name
		case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
			[ll, df, dv] = E_log_p(lik_name, y, post_m, post_v, hyp.lik);
		otherwise	 
			[ll,df,d2f,dv] = likKL(post_v, lik, hyp.lik, y, post_m);
		end
	end

	alpha=R0'*R0*(m_u-m_0);

	nlZ_it=batch_nlz_sparse(mean, lik, x,  y,  hyp, R0, Ku, m_0, diagK, R, C, m_u);
	D=-diag(lambda);
	nlZ_it2=batch_nlz_sparsev2(mean, lik, x,  y,  hyp, Ku, Kuu, m_0, m_u, d0, D)
	fprintf('%d) %.4f %.4f\n', it, nlZ_it, nlZ_it2);
	%fprintf('%d) %.4f\n', it, nlZ_it);

	%g_rate=rate/(2.0+it)^.2;
	g_rate=rate;
	r_it=1.0/(1.0+g_rate);

	if it==0
		m_u=m_u+(1-r_it)*Ku*df;
		lambda=(1-r_it)*2.0*dv;
	else
		m_u=m_u+g_rate*( (g_rate*R0'*R0+ inv_V_u)\ (H*df - alpha) );
		lambda=(1.0-r_it)*(2.0*dv)+r_it*lambda;
	end

	inv_V_u=( R0'*R0 - H*diag(lambda)*H' );
	V_u=(inv_V_u)\eye(nu);

	it=it+1;
end
C=chol(V_u,'lower');
alpha=R0'*R0*(m_u-m_0);

tmp1=C'*R0';
post.alpha=alpha;
post.L=R0'*(tmp1'*tmp1-eye(nu))*R0;
post.sW=zeros(n);

%H=Kuu\Ku;
D=-diag(lambda);
post.L2=-(   Kuu*((Ku*D*Ku')\Kuu) + Kuu  )\eye(nu);


nlZ=batch_nlz_sparse(mean, lik, x,  y,  hyp, R0, Ku, m_0, diagK, R, C, m_u);
fprintf('final: %.4f\n', nlZ);

if nargout>2                                           % do we want derivatives?
  warning('to be implemented\n');
  dnlZ = NaN;
end
